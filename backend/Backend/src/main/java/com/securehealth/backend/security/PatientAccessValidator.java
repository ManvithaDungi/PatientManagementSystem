package com.securehealth.backend.security;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.service.ConsentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Component for enforcing granular row-level access control for patient data.
 * <p>
 * Ensures that patients can only access their own records, while providing
 * bypasses for staff roles like DOCTOR and ADMIN. Optionally also enforces
 * that a provider role (DOCTOR/NURSE/LAB_TECHNICIAN) has active patient consent
 * for the specific data category being accessed.
 * </p>
 */
@Component
public class PatientAccessValidator {

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private ConsentService consentService;

    /**
     * Validates if the currently authenticated user is authorized to access
     * the specified patient's data. No consent check is performed - use this
     * overload only for data categories that are not consent-gated (e.g. appointments).
     *
     * @param patientId the ID of the patient record being accessed
     * @param auth the current {@link Authentication} object
     * @throws RuntimeException if access is forbidden or the patient is not found
     */
    public void validateAccess(Long patientId, Authentication auth) {
        validateAccess(patientId, roleOf(auth), auth.getName(), null);
    }

    /**
     * Validates access the same way as {@link #validateAccess(Long, Authentication)},
     * but additionally requires that a provider role (DOCTOR/NURSE/LAB_TECHNICIAN) hold
     * active patient consent for the given data category.
     *
     * @param patientId the ID of the patient record being accessed
     * @param auth the current {@link Authentication} object
     * @param consentType the consent category required for provider access (e.g. "MEDICAL_RECORDS")
     * @throws RuntimeException if access is forbidden or the patient is not found
     */
    public void validateAccess(Long patientId, Authentication auth, String consentType) {
        validateAccess(patientId, roleOf(auth), auth.getName(), consentType);
    }

    /**
     * Core access check, usable outside of a Spring Security {@link Authentication}
     * (e.g. from services that only have the requester's email/role on hand).
     *
     * @param patientId the ID of the patient profile being accessed
     * @param role the requester's role (e.g. "DOCTOR", "PATIENT")
     * @param email the requester's email
     * @param consentType if non-null, the consent category a provider role must hold; ignored for ADMIN/PATIENT
     * @throws RuntimeException if access is forbidden or the patient is not found
     */
    public void validateAccess(Long patientId, String role, String email, String consentType) {
        // Admins have full oversight access, no consent check required.
        if ("ADMIN".equals(role)) {
            return;
        }

        if ("DOCTOR".equals(role) || "NURSE".equals(role) || "LAB_TECHNICIAN".equals(role)) {
            if (consentType != null) {
                Login provider = loginRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("404: Provider not found"));
                if (!consentService.hasConsent(patientId, provider.getUserId(), consentType)) {
                    throw new RuntimeException("403 Forbidden: No active patient consent on file for " + consentType);
                }
            }
            return;
        }

        // For patients (or any other/unknown role), verify the profile belongs to their JWT email
        PatientProfile profile = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("404: Patient not found"));

        if (!profile.getUser().getEmail().equals(email)) {
            throw new RuntimeException("403 Forbidden: You cannot access another patient's records");
        }
    }

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("UNKNOWN");
    }
}