package com.securehealth.backend.dto;

import com.securehealth.backend.model.Consent;
import lombok.Data;

import java.time.LocalDateTime;

/** API view of a consent grant (never the JPA entity, so no lazy association is serialized). */
@Data
public class ConsentView {
    private Long id;
    private Long patientId;
    private NurseViews.UserRef grantedTo;
    private String consentType;
    private String status;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String reason;
    private String revocationReason;

    public static ConsentView of(Consent c) {
        ConsentView v = new ConsentView();
        v.setId(c.getId());
        v.setPatientId(c.getPatient() == null ? null : c.getPatient().getProfileId());
        v.setGrantedTo(NurseViews.user(c.getGrantedTo()));
        v.setConsentType(c.getConsentType());
        v.setStatus(c.getStatus());
        v.setGrantedAt(c.getGrantedAt());
        v.setExpiresAt(c.getExpiresAt());
        v.setRevokedAt(c.getRevokedAt());
        v.setReason(c.getReason());
        v.setRevocationReason(c.getRevocationReason());
        return v;
    }
}
