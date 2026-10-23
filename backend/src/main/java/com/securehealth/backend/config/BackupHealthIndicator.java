package com.securehealth.backend.config;

import com.securehealth.backend.service.BackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * Surfaces the outcome of the nightly backup in {@code /actuator/health}. A failed backup reports
 * DEGRADED (HTTP 200) so it is visible to monitoring without taking the application out of rotation.
 */
@Component("backup")
public class BackupHealthIndicator implements HealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED");

    @Autowired(required = false)
    private BackupService backupService;

    @Override
    public Health health() {
        if (backupService == null) {
            return Health.up().withDetail("note", "backups are disabled").build();
        }
        if (backupService.isLastBackupFailed()) {
            return Health.status(DEGRADED)
                    .withDetail("lastFailure", String.valueOf(backupService.getLastFailure()))
                    .withDetail("reason", backupService.getLastFailureReason())
                    .build();
        }
        Health.Builder b = Health.up();
        if (backupService.getLastSuccess() != null) {
            b.withDetail("lastSuccess", backupService.getLastSuccess().toString());
        } else {
            b.withDetail("note", "no backup has run yet");
        }
        return b.build();
    }
}
