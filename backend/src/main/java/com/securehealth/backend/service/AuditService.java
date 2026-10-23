package com.securehealth.backend.service;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes explicit audit events. Details must contain identifiers and action names only, never clinical
 * content. Runs in its own transaction so that an audit failure cannot poison (or be rolled back with)
 * the business transaction that triggered it.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorEmail, String action, String details) {
        try {
            auditLogRepository.save(new AuditLog(
                    actorEmail == null ? "SYSTEM" : actorEmail, action, "INTERNAL", "SYSTEM", details));
        } catch (RuntimeException e) {
            log.error("Failed to persist audit event {}", action, e);
        }
    }
}
