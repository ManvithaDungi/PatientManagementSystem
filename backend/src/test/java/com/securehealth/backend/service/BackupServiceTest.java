package com.securehealth.backend.service;

import com.securehealth.backend.config.BackupHealthIndicator;
import com.securehealth.backend.util.AesGcmFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    private static String newKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void aesGcmFiles_roundTrips_andDetectsTampering(@TempDir Path dir) throws Exception {
        SecretKey key = AesGcmFiles.keyFromBase64(newKey());
        Path plain = dir.resolve("dump.bin");
        Files.write(plain, "patient data that must not be readable".getBytes(StandardCharsets.UTF_8));

        Path enc = dir.resolve("dump.enc");
        AesGcmFiles.encrypt(plain, enc, key);
        assertFalse(new String(Files.readAllBytes(enc), StandardCharsets.ISO_8859_1).contains("patient data"),
                "ciphertext must not contain the plaintext");

        Path back = dir.resolve("back.bin");
        AesGcmFiles.decrypt(enc, back, key);
        assertArrayEquals(Files.readAllBytes(plain), Files.readAllBytes(back));

        byte[] tampered = Files.readAllBytes(enc);
        tampered[tampered.length - 1] ^= 0x01;
        Files.write(enc, tampered);
        assertThrows(IOException.class, () -> AesGcmFiles.decrypt(enc, dir.resolve("bad.bin"), key));
    }

    @Test
    void failedBackup_isReportedAsDegradedHealth_andLeavesNoPlaintext(@TempDir Path dir) throws Exception {
        BackupService service = new BackupService();
        ReflectionTestUtils.setField(service, "backupDir", dir.toString());
        ReflectionTestUtils.setField(service, "retentionDays", 7);
        // Nothing listens here, so pg_dump (or its absence) makes the run fail.
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:postgresql://127.0.0.1:1/none");
        ReflectionTestUtils.setField(service, "dbUsername", "u");
        ReflectionTestUtils.setField(service, "dbPassword", "p");
        ReflectionTestUtils.setField(service, "encryptionKey", newKey());

        BackupHealthIndicator indicator = new BackupHealthIndicator();
        ReflectionTestUtils.setField(indicator, "backupService", service);
        assertEquals("UP", indicator.health().getStatus().getCode());

        service.performBackup();

        assertTrue(service.isLastBackupFailed());
        Health health = indicator.health();
        assertEquals("DEGRADED", health.getStatus().getCode());
        assertNotNull(health.getDetails().get("reason"));
        try (var files = Files.list(dir)) {
            assertEquals(0, files.count(), "no partial or plaintext dump may remain");
        }
    }
}
