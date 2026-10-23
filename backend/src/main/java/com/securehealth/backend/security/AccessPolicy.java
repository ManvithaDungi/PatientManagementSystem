package com.securehealth.backend.security;

import com.securehealth.backend.exception.ForbiddenException;
import com.securehealth.backend.exception.NotFoundException;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.service.ConsentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Single place that decides whether a user may read or write a patient's clinical data.
 * <p>
 * Inputs: the caller's role, the care relationship (assigned doctor/nurse, or a doctor with a
 * SCHEDULED/COMPLETED appointment) and effective consent (ACTIVE and not expired, for the specific
 * category or ALL).
 * <ul>
 *   <li>PATIENT: own record only (read); never writes clinical data through these checks.</li>
 *   <li>ADMIN: oversight read only; never a clinical writer.</li>
 *   <li>DOCTOR / NURSE / LAB_TECHNICIAN: care relationship (doctor, nurse) OR effective consent.</li>
 * </ul>
 */
@Component
@Transactional(readOnly = true)
public class AccessPolicy {

    private static final List<AppointmentStatus> CARE_STATUSES =
            List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED);

    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private ConsentService consentService;
    @Autowired private AppointmentRepository appointmentRepository;

    public AccessPolicy() {
    }

    public AccessPolicy(PatientProfileRepository patientProfileRepository, LoginRepository loginRepository,
                        ConsentService consentService, AppointmentRepository appointmentRepository) {
        this.patientProfileRepository = patientProfileRepository;
        this.loginRepository = loginRepository;
        this.consentService = consentService;
        this.appointmentRepository = appointmentRepository;
    }

    /** Throws {@link ForbiddenException} unless the caller may read {@code consentType} data of the patient. */
    public void checkRead(Long patientId, String role, String email, String consentType) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (isProvider(role)) {
            requireProviderAccess(patientId, role, email, consentType);
            return;
        }
        requireOwner(patientId, email);
    }

    /** Throws {@link ForbiddenException} unless the caller may write {@code consentType} data for the patient. */
    public void checkWrite(Long patientId, String role, String email, String consentType) {
        if (!isProvider(role)) {
            throw new ForbiddenException("403 Forbidden: Your role cannot record clinical data.");
        }
        requireProviderAccess(patientId, role, email, consentType);
    }

    public boolean canRead(Long patientId, String role, String email, String consentType) {
        try {
            checkRead(patientId, role, email, consentType);
            return true;
        } catch (ForbiddenException | NotFoundException e) {
            return false;
        }
    }

    /** True if the user has a treating relationship with the patient (not consent). */
    public boolean hasCareRelationship(Long patientId, String role, String email) {
        if ("DOCTOR".equals(role)) {
            if (isAssigned(patientId, email, true)) {
                return true;
            }
            Login doctor = loginRepository.findByEmail(email).orElse(null);
            return doctor != null && appointmentRepository.existsByDoctor_UserIdAndPatient_ProfileIdAndStatusIn(
                    doctor.getUserId(), patientId, CARE_STATUSES);
        }
        if ("NURSE".equals(role)) {
            return isAssigned(patientId, email, false);
        }
        return false;
    }

    private void requireProviderAccess(Long patientId, String role, String email, String consentType) {
        if (hasCareRelationship(patientId, role, email)) {
            return;
        }
        Login provider = loginRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("404: Provider not found"));
        if (consentType != null && consentService.hasConsent(patientId, provider.getUserId(), consentType)) {
            return;
        }
        throw new ForbiddenException("403 Forbidden: No active patient consent on file for "
                + (consentType == null ? "this patient" : consentType));
    }

    private void requireOwner(Long patientId, String email) {
        PatientProfile profile = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("404: Patient not found"));
        if (profile.getUser() == null || !profile.getUser().getEmail().equals(email)) {
            throw new ForbiddenException("403 Forbidden: You cannot access another patient's records");
        }
    }

    private boolean isAssigned(Long patientId, String email, boolean doctor) {
        return patientProfileRepository.findById(patientId)
                .map(p -> {
                    Login assigned = doctor ? p.getAssignedDoctor() : p.getAssignedNurse();
                    return assigned != null && email != null && email.equals(assigned.getEmail());
                })
                .orElse(false);
    }

    private static boolean isProvider(String role) {
        return "DOCTOR".equals(role) || "NURSE".equals(role) || "LAB_TECHNICIAN".equals(role);
    }
}
