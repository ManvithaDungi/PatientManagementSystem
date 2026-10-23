package com.securehealth.backend.controller;

import com.securehealth.backend.model.Consent;
import com.securehealth.backend.service.ConsentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for managing patient consents.
 * <p>
 * This controller provides endpoints for patients to list their consents, 
 * grant new ones, and revoke existing ones. Access is restricted to users with PATIENT authority.
 * </p>
 */
@RestController
@RequestMapping("/api/consent")
@PreAuthorize("hasAuthority('PATIENT')")
public class ConsentController {

    @Autowired
    private ConsentService consentService;

    /**
     * GET /api/consent — List all consents for the logged-in patient.
     */
    @GetMapping
    public ResponseEntity<?> getMyConsents(Authentication authentication) {
        return ResponseEntity.ok(consentService.getMyConsents(authentication.getName()));
    }

    /**
     * POST /api/consent — Grant a new consent.
     * Body: { "grantedToId": 5, "consentType": "MEDICAL_RECORDS", "reason": "...", "expiresAt": "..." }
     */
    @PostMapping
    public ResponseEntity<?> grantConsent(@RequestBody Map<String, Object> payload, Authentication authentication) {
        com.securehealth.backend.dto.ConsentView consent = consentService.grantConsent(authentication.getName(), payload);
        return ResponseEntity.status(201).body(consent);
    }

    /**
     * PUT /api/consent/{id}/revoke — Revoke a consent.
     */
    @GetMapping("/access-log")
    public ResponseEntity<?> getAccessLog(Authentication authentication) {
        return ResponseEntity.ok(consentService.getAccessLog(authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateConsent(@PathVariable Long id, @RequestBody Map<String, Object> payload,
                                           Authentication authentication) {
        return ResponseEntity.ok(consentService.updateConsent(authentication.getName(), id, payload));
    }

    @PutMapping("/{id}/revoke")
    public ResponseEntity<?> revokeConsent(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> body,
                                           Authentication authentication) {
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        com.securehealth.backend.dto.ConsentView consent = consentService.revokeConsent(authentication.getName(), id, reason);
        return ResponseEntity.ok(consent);
    }
}
