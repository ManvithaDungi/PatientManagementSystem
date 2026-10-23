package com.securehealth.backend.controller;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrative audit-log browsing. Always paginated, newest first.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Pages through the audit log, newest first.
     *
     * @param page  zero-based page index (default 0)
     * @param size  page size (default 50, capped at 200)
     * @param email optional exact user filter
     * @param from  optional inclusive lower bound (ISO date-time)
     * @param to    optional inclusive upper bound (ISO date-time)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(query(page, size, email, from, to));
    }

    /** Same as {@link #getLogs} but for one user, kept for investigation links. */
    @GetMapping("/{email}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserLogs(
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(query(page, size, email, null, null));
    }

    private Map<String, Object> query(int page, int size, String email, LocalDateTime from, LocalDateTime to) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (email != null && !email.isBlank()) {
                predicates.add(cb.equal(root.get("email"), email.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> result = auditLogRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp", "id")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return body;
    }
}
