package com.securehealth.backend.service;

import com.securehealth.backend.util.AesGcmFiles;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * Nightly PostgreSQL backup (pg_dump, custom format).
 * <p>
 * The dump is encrypted at rest with AES-256-GCM (the application encryption key) and the plaintext
 * file is removed immediately. The outcome is exposed through the {@code calyx.backup.*} metrics and
 * the {@code backup} health indicator, so a failure is visible without reading logs.
 * Restore: decrypt with {@link AesGcmFiles#decrypt} and feed the result to {@code pg_restore}.
 * </p>
 */
@Service
@ConditionalOnProperty(name = "backup.enabled", havingValue = "true", matchIfMissing = true)
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Value("${backup.directory:./backups}")
    private String backupDir;

    @Value("${backup.retention-days:7}")
    private int retentionDays;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private volatile Instant lastSuccess;
    private volatile Instant lastFailure;
    private volatile String lastFailureReason;
    private Counter failureCounter;

    @PostConstruct
    void registerMetrics() {
        if (meterRegistry != null) {
            Gauge.builder("calyx.backup.last.success.timestamp.seconds", this,
                    b -> b.lastSuccess == null ? 0 : b.lastSuccess.getEpochSecond()).register(meterRegistry);
            failureCounter = Counter.builder("calyx.backup.failures.total").register(meterRegistry);
        }
    }

    @Scheduled(cron = "${backup.cron:0 0 2 * * *}")
    public void performBackup() {
        log.info("Starting scheduled database backup...");
        Path tmp = null;
        try {
            Path dirPath = Paths.get(backupDir);
            Files.createDirectories(dirPath);

            String timestamp = LocalDateTime.now().format(FMT);
            tmp = dirPath.resolve("backup_" + timestamp + ".dump.tmp");
            Path encrypted = dirPath.resolve("backup_" + timestamp + ".dump.enc");

            ProcessBuilder pb = new ProcessBuilder(
                    "pg_dump",
                    "-h", extractHost(datasourceUrl),
                    "-p", extractPort(datasourceUrl),
                    "-U", dbUsername,
                    "-F", "c",     // custom format (compressed)
                    "-f", tmp.toAbsolutePath().toString(),
                    extractDbName(datasourceUrl)
            );
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                recordFailure("pg_dump exited with code " + exitCode + ": " + abbreviate(output));
                return;
            }

            SecretKey key = AesGcmFiles.keyFromBase64(encryptionKey);
            AesGcmFiles.encrypt(tmp, encrypted, key);
            long sizeKb = Files.size(encrypted) / 1024;
            lastSuccess = Instant.now();
            log.info("Backup completed successfully: {} ({} KB, encrypted)", encrypted.getFileName(), sizeKb);
            cleanOldBackups(dirPath);
        } catch (Exception e) {
            recordFailure(e.getClass().getSimpleName() + ": " + abbreviate(e.getMessage()));
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp); // never leave the plaintext dump behind
                } catch (IOException e) {
                    log.warn("Could not delete temporary plaintext dump {}", tmp.getFileName());
                }
            }
        }
    }

    private void recordFailure(String reason) {
        lastFailure = Instant.now();
        lastFailureReason = reason;
        if (failureCounter != null) {
            failureCounter.increment();
        }
        log.error("Backup failed: {}", reason);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** True when the most recent attempt failed (or no attempt has succeeded after a failure). */
    public boolean isLastBackupFailed() {
        return lastFailure != null && (lastSuccess == null || lastFailure.isAfter(lastSuccess));
    }

    public Instant getLastSuccess() {
        return lastSuccess;
    }

    public Instant getLastFailure() {
        return lastFailure;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    private void cleanOldBackups(Path dirPath) {
        log.info("Cleaning backups older than {} days...", retentionDays);
        try (Stream<Path> files = Files.list(dirPath)) {
            LocalDate cutoff = LocalDate.now().minus(retentionDays, ChronoUnit.DAYS);
            files.filter(f -> f.getFileName().toString().startsWith("backup_"))
                    .filter(f -> {
                        try {
                            return Files.getLastModifiedTime(f).toInstant()
                                    .isBefore(cutoff.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(f -> {
                        try {
                            Files.delete(f);
                            log.info("Deleted old backup: {}", f.getFileName());
                        } catch (IOException e) {
                            log.warn("Failed to delete old backup: {}", f.getFileName());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to clean old backups: {}", e.getMessage());
        }
    }

    private String extractHost(String url) {
        String afterProtocol = url.split("//")[1];
        return afterProtocol.split("[:/]")[0];
    }

    private String extractPort(String url) {
        String afterProtocol = url.split("//")[1];
        String hostPort = afterProtocol.split("/")[0];
        return hostPort.contains(":") ? hostPort.split(":")[1] : "5432";
    }

    private String extractDbName(String url) {
        String afterProtocol = url.split("//")[1];
        String afterHost = afterProtocol.split("/")[1];
        return afterHost.split("\\?")[0];
    }
}
