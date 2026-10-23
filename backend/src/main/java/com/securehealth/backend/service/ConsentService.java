package com.securehealth.backend.service;

import com.securehealth.backend.model.Consent;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.ConsentRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ConsentService {

    /** The data categories a patient can grant, matching the types the access validator enforces. */
    public static final java.util.Set<String> CONSENT_TYPES =
            java.util.Set.of("ALL", "MEDICAL_RECORDS", "LAB_RESULTS", "PRESCRIPTIONS", "VITAL_SIGNS");

    private static final java.util.Set<com.securehealth.backend.model.Role> GRANTEE_ROLES =
            java.util.Set.of(com.securehealth.backend.model.Role.DOCTOR,
                    com.securehealth.backend.model.Role.NURSE,
                    com.securehealth.backend.model.Role.LAB_TECHNICIAN);

    @Autowired private ConsentRepository consentRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private AuditService auditService;
    @Autowired private com.securehealth.backend.repository.AuditLogRepository auditLogRepository;

    /**
     * Retrieves all consent records for the currently logged-in patient.
     *
     * @param patientEmail the email of the patient
     * @return a list of {@link Consent} entities
     */
    public List<com.securehealth.backend.dto.ConsentView> getMyConsents(String patientEmail) {
        PatientProfile profile = getPatientProfile(patientEmail);
        return consentRepository.findByPatient_ProfileIdOrderByGrantedAtDesc(profile.getProfileId()).stream()
                .map(com.securehealth.backend.dto.ConsentView::of).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Grants a provider access to a specific type of patient data.
     *
     * @param patientEmail the email of the patient granting consent
     * @param payload a map containing 'grantedToId', 'consentType', 'reason', and optional 'expiresAt'
     * @return the saved {@link Consent} entity
     * @throws RuntimeException if the provider is not found or an active consent already exists
     */
    public com.securehealth.backend.dto.ConsentView grantConsent(String patientEmail, Map<String, Object> payload) {
        PatientProfile profile = getPatientProfile(patientEmail);

        if (payload.get("grantedToId") == null) {
            throw new com.securehealth.backend.exception.BadRequestException("Please choose a provider to grant access to.");
        }
        Long grantedToId = Long.valueOf(payload.get("grantedToId").toString());
        String consentType = payload.getOrDefault("consentType", "ALL").toString().toUpperCase();
        String reason = payload.getOrDefault("reason", "").toString();

        if (!CONSENT_TYPES.contains(consentType)) {
            throw new com.securehealth.backend.exception.BadRequestException("Unsupported consent type: " + consentType
                    + ". Allowed: " + String.join(", ", new java.util.TreeSet<>(CONSENT_TYPES)));
        }

        // Validate the provider exists and is clinical staff
        Login provider = loginRepository.findById(grantedToId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Provider not found with id: " + grantedToId));
        if (!GRANTEE_ROLES.contains(provider.getRole())) {
            throw new com.securehealth.backend.exception.BadRequestException("Consent can only be granted to a doctor, nurse or lab technician.");
        }

        expireStaleConsents();

        // Check for duplicate active consent
        if (consentRepository.existsByPatient_ProfileIdAndGrantedTo_UserIdAndConsentTypeAndStatus(
                profile.getProfileId(), grantedToId, consentType, "ACTIVE")) {
            throw new com.securehealth.backend.exception.ConflictException("Active consent already exists for this provider and data type.");
        }

        Consent consent = new Consent();
        consent.setPatient(profile);
        consent.setGrantedTo(provider);
        consent.setConsentType(consentType);
        consent.setStatus("ACTIVE");
        consent.setReason(reason);

        // Optional expiry
        if (payload.containsKey("expiresAt") && payload.get("expiresAt") != null) {
            LocalDateTime expiresAt = parseExpiry(payload.get("expiresAt").toString());
            if (!expiresAt.isAfter(LocalDateTime.now())) {
                throw new com.securehealth.backend.exception.BadRequestException("The expiry date must be in the future.");
            }
            consent.setExpiresAt(expiresAt);
        }

        try {
            Consent saved = consentRepository.saveAndFlush(consent);
            auditService.record(patientEmail, "CONSENT_GRANTED", "consent=" + saved.getId()
                    + " provider=" + grantedToId + " type=" + consentType);
            return com.securehealth.backend.dto.ConsentView.of(saved);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Lost a race with a concurrent identical grant (UNIQUE active_key).
            throw new com.securehealth.backend.exception.ConflictException("Active consent already exists for this provider and data type.");
        }
    }

    /**
     * Revokes an existing consent record.
     *
     * @param patientEmail the email of the patient revoking consent
     * @param consentId the ID of the consent record to revoke
     * @return the updated {@link Consent} entity
     * @throws RuntimeException if the consent is not found, not owned by the patient, or already revoked
     */
    public com.securehealth.backend.dto.ConsentView revokeConsent(String patientEmail, Long consentId) {
        return revokeConsent(patientEmail, consentId, null);
    }

    public com.securehealth.backend.dto.ConsentView revokeConsent(String patientEmail, Long consentId, String reason) {
        PatientProfile profile = getPatientProfile(patientEmail);

        Consent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Consent not found with id: " + consentId));

        // Ensure the patient owns this consent
        if (!consent.getPatient().getProfileId().equals(profile.getProfileId())) {
            throw new com.securehealth.backend.exception.ForbiddenException("You are not authorized to revoke this consent.");
        }

        if ("REVOKED".equals(consent.getStatus())) {
            throw new com.securehealth.backend.exception.BadRequestException("This consent has already been revoked.");
        }

        consent.setStatus("REVOKED");
        if (reason != null && !reason.isBlank()) {
            consent.setRevocationReason(reason.trim().length() > 1000 ? reason.trim().substring(0, 1000) : reason.trim());
        }
        consent.setRevokedAt(LocalDateTime.now());

        Consent saved = consentRepository.save(consent);
        auditService.record(patientEmail, "CONSENT_REVOKED", "consent=" + saved.getId()
                + " type=" + saved.getConsentType());
        return com.securehealth.backend.dto.ConsentView.of(saved);
    }

    /**
     * Validates if a healthcare provider has active consent for a patient's data.
     * <p>
     * Checks for either the specific data type or a general "ALL" consent.
     * </p>
     *
     * @param patientProfileId the patient's profile ID
     * @param providerUserId the provider's user ID
     * @param consentType the type of data being accessed (e.g., "VITALS", "RECORDS")
     * @return true if active consent is found, false otherwise
     */
    /** Changes the expiry and/or note of an ACTIVE consent (provider and category cannot change). */
    public com.securehealth.backend.dto.ConsentView updateConsent(String patientEmail, Long consentId, Map<String, Object> payload) {
        PatientProfile profile = getPatientProfile(patientEmail);
        Consent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Consent not found with id: " + consentId));
        if (!consent.getPatient().getProfileId().equals(profile.getProfileId())) {
            throw new com.securehealth.backend.exception.ForbiddenException("You are not authorized to modify this consent.");
        }
        if (!"ACTIVE".equals(consent.getStatus())) {
            throw new com.securehealth.backend.exception.BadRequestException("Only an active consent can be modified.");
        }
        if (payload.containsKey("expiresAt")) {
            Object raw = payload.get("expiresAt");
            if (raw == null || raw.toString().isBlank()) {
                consent.setExpiresAt(null);
            } else {
                LocalDateTime expiresAt = parseExpiry(raw.toString());
                if (!expiresAt.isAfter(LocalDateTime.now())) {
                    throw new com.securehealth.backend.exception.BadRequestException("The expiry date must be in the future.");
                }
                consent.setExpiresAt(expiresAt);
            }
        }
        if (payload.containsKey("reason")) {
            Object reason = payload.get("reason");
            consent.setReason(reason == null ? "" : reason.toString());
        }
        Consent saved = consentRepository.save(consent);
        auditService.record(patientEmail, "CONSENT_UPDATED", "consent=" + saved.getId());
        return com.securehealth.backend.dto.ConsentView.of(saved);
    }

    /** Who read this patient's clinical data, newest first (built from the audit trail). */
    public List<Map<String, Object>> getAccessLog(String patientEmail) {
        PatientProfile profile = getPatientProfile(patientEmail);
        return auditLogRepository
                .findTop100ByActionAndDetailsEndingWithOrderByTimestampDesc("PATIENT_DATA_READ", " patient=" + profile.getProfileId())
                .stream()
                .map(a -> {
                    String category = a.getDetails() == null ? "" : a.getDetails().split(" ")[0];
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("accessor", a.getEmail());
                    m.put("category", category);
                    m.put("action", "Viewed " + category.toLowerCase().replace('_', ' '));
                    m.put("timestamp", a.getTimestamp());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean hasConsent(Long patientProfileId, Long providerUserId, String consentType) {
        // Specific type OR "ALL"; an expired consent no longer grants access.
        return consentRepository.existsEffective(
                patientProfileId, providerUserId, java.util.List.of(consentType, "ALL"), LocalDateTime.now());
    }

    /** Marks ACTIVE consents past their expiry as EXPIRED (frees the unique active slot for re-granting). */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */10 * * * *")
    public int expireStaleConsents() {
        List<Consent> stale = consentRepository.findByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());
        for (Consent c : stale) {
            c.setStatus("EXPIRED");
            consentRepository.save(c);
            auditService.record("SYSTEM", "CONSENT_EXPIRED", "consent=" + c.getId());
        }
        return stale.size();
    }

    private static LocalDateTime parseExpiry(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (java.time.format.DateTimeParseException notLocal) {
            try {
                return java.time.OffsetDateTime.parse(raw)
                        .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (java.time.format.DateTimeParseException invalid) {
                throw new com.securehealth.backend.exception.BadRequestException("Invalid expiry date: " + raw);
            }
        }
    }

    private PatientProfile getPatientProfile(String email) {
        Login user = loginRepository.findByEmail(email)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("User not found with email: " + email));
        return patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Patient profile not found for user: " + email));
    }
}
