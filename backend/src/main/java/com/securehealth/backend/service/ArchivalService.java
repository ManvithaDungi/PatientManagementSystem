package com.securehealth.backend.service;

import com.securehealth.backend.model.ArchivedUser;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.repository.ArchivedUserRepository;
import com.securehealth.backend.repository.LoginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service that archives inactive user accounts.
 * Users who haven't logged in for a configurable number of days are flagged
 * as archived. Their data remains intact for potential restoration by an admin.
 */
@Service
@ConditionalOnProperty(name = "archival.enabled", havingValue = "true", matchIfMissing = true)
public class ArchivalService {

    private static final Logger log = LoggerFactory.getLogger(ArchivalService.class);

    @Autowired private LoginRepository loginRepository;
    @Autowired private ArchivedUserRepository archivedUserRepository;

    @Value("${archival.inactivity-days:365}")
    private int inactivityDays;

    /**
     * Runs on the configured cron schedule (default: every Sunday at 3 AM).
     * Identifies users with no login activity beyond the inactivity threshold
     * and archives them.
     */
    @Scheduled(cron = "${archival.cron:0 0 3 * * SUN}")
    @Transactional
    public void archiveInactiveUsers() {
        log.info("Starting scheduled archival of inactive users (threshold: {} days)...", inactivityDays);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(inactivityDays);

        // Find users who either:
        // 1. Have lastLoginAt older than cutoff, OR
        // 2. Have never logged in (lastLoginAt is null) AND createdAt is older than cutoff
        List<Login> allUsers = loginRepository.findAll();

        int archivedCount = 0;
        for (Login user : allUsers) {
            // Skip already archived users
            if (Boolean.TRUE.equals(user.getArchived())) continue;

            boolean isInactive = false;

            if (user.getLastLoginAt() != null) {
                isInactive = user.getLastLoginAt().isBefore(cutoffDate);
            }
            // Accounts with no recorded login are NOT archived: inactivity cannot be established, and
            // archived accounts are now blocked from signing in.

            if (isInactive) {
                archiveUser(user);
                archivedCount++;
            }
        }

        log.info("Archival complete. {} users archived.", archivedCount);
    }

    private void archiveUser(Login user) {
        // Create archive record
        ArchivedUser archive = new ArchivedUser();
        archive.setOriginalUserId(user.getUserId());
        archive.setEmail(user.getEmail());
        archive.setRole(user.getRole());
        archive.setLastActiveAt(user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getCreatedAt());
        archive.setReason("Automatic archival: inactive for " + inactivityDays + " days.");

        archivedUserRepository.save(archive);

        // Flag the user as archived
        user.setArchived(true);
        loginRepository.save(user);

        log.info("Archived user: {} (ID: {})", user.getEmail(), user.getUserId());
    }

    /**
     * Restores a previously archived user account.
     * <p>
     * Re-enables the core login account and removes the archival snapshot.
     * </p>
     *
     * @param archivedUserId the ID of the archive record
     * @return the restored {@link Login} entity
     * @throws RuntimeException if the archive or original user is not found
     */
    @Transactional
    public Login restoreUser(Long archivedUserId) {
        ArchivedUser archive = archivedUserRepository.findById(archivedUserId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Archived user not found with id: " + archivedUserId));

        Login user = loginRepository.findById(archive.getOriginalUserId())
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Original user account not found for id: " + archive.getOriginalUserId()));

        user.setArchived(false);
        loginRepository.save(user);

        archivedUserRepository.delete(archive);

        log.info("Restored user: {} (ID: {})", user.getEmail(), user.getUserId());
        return user;
    }

    /**
     * Retrieves a list of all archived user snapshots.
     *
     * @return a list of {@link ArchivedUser} entities
     */
    public List<ArchivedUser> getArchivedUsers() {
        return archivedUserRepository.findAllByOrderByArchivedAtDesc();
    }
}
