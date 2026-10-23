package com.securehealth.backend.service;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only operational insight for the admin console, derived from the audit
 * log and the running JVM rather than from static data.
 */
@Service
public class AdminInsightsService {

    /** Audit actions an administrator should look at. */
    static final Set<String> SECURITY_ACTIONS = Set.of(
            "LOGIN_FAILED", "LOGIN_BLOCKED", "ACCOUNT_LOCKED", "OTP_BLOCKED", "OTP_LIMIT_REACHED",
            "TOKEN_THEFT_DETECTED", "INVALID_REFRESH_TOKEN", "UNAUTHORIZED_ACCESS", "FORBIDDEN_ACCESS",
            "PASSWORD_RESET_FAILED", "REGISTRATION_FAILED", "SYSTEM_ERROR");

    static final int MAX_EVENTS = 200;
    private static final int MAX_HOURS = 24 * 90;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** Security-relevant audit entries from the last {@code hours} hours, newest first. */
    public List<AuditLog> securityEvents(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(clamp(hours, 1, MAX_HOURS));
        return auditLogRepository.findByActionInAndTimestampGreaterThanEqualOrderByTimestampDesc(
                SECURITY_ACTIONS, since, PageRequest.of(0, clamp(limit, 1, MAX_EVENTS)));
    }

    /** Audit-event counts per action for a timeframe such as {@code 24h}, {@code 7d} or {@code 30d}. */
    public Map<String, Object> userActivity(String timeframe) {
        LocalDateTime since = LocalDateTime.now().minusHours(parseHours(timeframe));

        Map<String, Long> byAction = new LinkedHashMap<>();
        long total = 0;
        long security = 0;
        for (Object[] row : auditLogRepository.countByActionSince(since)) {
            String action = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            byAction.put(action, count);
            total += count;
            if (SECURITY_ACTIONS.contains(action)) {
                security += count;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timeframe", timeframe);
        body.put("since", since);
        body.put("totalEvents", total);
        body.put("securityEvents", security);
        body.put("successfulLogins", byAction.getOrDefault("LOGIN_SUCCESS", 0L));
        body.put("failedLogins", byAction.getOrDefault("LOGIN_FAILED", 0L));
        body.put("byAction", byAction);
        return body;
    }

    /** Live status of the backend's own dependencies and JVM. */
    public Map<String, Object> systemHealth() {
        long started = System.nanoTime();
        boolean dbUp = false;
        try (Connection c = dataSource.getConnection()) {
            dbUp = c.isValid(2);
        } catch (Exception ignored) {
            // reported as DOWN
        }
        long dbMillis = (System.nanoTime() - started) / 1_000_000;

        boolean redisUp = false;
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
                redisUp = "PONG".equalsIgnoreCase(connection.ping());
            } catch (Exception ignored) {
                // reported as DOWN
            }
        }

        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();

        Long cpuPercent = null;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            double load = os.getCpuLoad();
            if (load >= 0) {
                cpuPercent = Math.round(load * 100.0);
            }
        }

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("status", dbUp ? "UP" : "DOWN");
        database.put("responseMillis", dbMillis);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? (redisUp ? "UP" : "DEGRADED") : "DOWN");
        body.put("database", database);
        body.put("redis", Map.of("status", redisUp ? "UP" : "DOWN"));
        body.put("memoryUsedPercent", max > 0 ? Math.round(used * 100.0 / max) : 0);
        body.put("cpuLoadPercent", cpuPercent);
        body.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        body.put("checkedAt", LocalDateTime.now());
        return body;
    }

    static int parseHours(String timeframe) {
        if (timeframe == null || timeframe.length() < 2) {
            return 24;
        }
        try {
            int n = Integer.parseInt(timeframe.substring(0, timeframe.length() - 1));
            return switch (Character.toLowerCase(timeframe.charAt(timeframe.length() - 1))) {
                case 'h' -> clamp(n, 1, MAX_HOURS);
                case 'd' -> clamp(n, 1, MAX_HOURS / 24) * 24;
                default -> 24;
            };
        } catch (NumberFormatException e) {
            return 24;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(Math.max(v, lo), hi);
    }
}
