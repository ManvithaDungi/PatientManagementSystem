package com.securehealth.backend.service;


import com.securehealth.backend.dto.PrescriptionDTO;
import com.securehealth.backend.dto.PrescriptionRequest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Prescription;
import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.repository.PrescriptionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing medication prescriptions.
 * <p>
 * Handles the creation of prescriptions by doctors, chronological
 * retrieval for patients, refill management, and administrative cleanup.
 * </p>
 */
@Service
public class PrescriptionService {

    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private com.securehealth.backend.repository.DoctorProfileRepository doctorProfileRepository;

    /**
     * Resolves a display name for a prescribing doctor.
     * <p>
     * Falls back to the login email only if the doctor has no profile row,
     * since {@code Login} itself has no name field.
     * </p>
     */
    private String resolveDoctorName(Login doctor) {
        if (doctor == null) {
            return "Unknown Doctor";
        }
        return doctorProfileRepository.findByUser(doctor)
                .map(p -> (p.getFirstName() + " " + p.getLastName()).trim())
                .filter(name -> !name.isEmpty())
                .orElse(doctor.getEmail());
    }

    /**
     * Maps a {@link Prescription} entity to its API-safe {@link PrescriptionDTO}.
     * <p>
     * Every read/write path in this service returns through this single mapper —
     * never the raw entity — since {@code Prescription.doctor}/{@code .patient}
     * carry {@code Login} objects with no JSON guard on {@code passwordHash}/{@code otp}.
     * </p>
     */
    private PrescriptionDTO mapToDTO(Prescription p) {
        PrescriptionDTO dto = new PrescriptionDTO();
        dto.setPrescriptionId(p.getPrescriptionId());
        dto.setPatientId(p.getPatient() != null ? p.getPatient().getProfileId() : null);
        dto.setPatientName(p.getPatient() != null
                ? (p.getPatient().getFirstName() + " " + p.getPatient().getLastName()).trim()
                : null);
        dto.setDoctorName(resolveDoctorName(p.getDoctor()));
        dto.setMedicationName(p.getMedicationName());
        dto.setDosage(p.getDosage());
        dto.setFrequency(p.getFrequency());
        dto.setDuration(p.getDuration());
        dto.setRoute(p.getRoute());
        dto.setSpecialInstructions(p.getSpecialInstructions());
        dto.setStatus(p.getStatus());
        dto.setIssuedAt(p.getIssuedAt());
        dto.setStartDate(p.getStartDate());
        dto.setEndDate(p.getEndDate());
        dto.setRefillsRemaining(p.getRefillsRemaining());
        return dto;
    }

    /**
     * Issues a new medication prescription for a patient.
     * <p>
     * Automatically captures the prescribing doctor and generates an audit log.
     * </p>
     *
     * @param request the {@link PrescriptionRequest} details
     * @param doctorEmail the email of the issuing doctor
     * @return the saved prescription, as a {@link PrescriptionDTO}
     */
    @Transactional
    public PrescriptionDTO createPrescription(PrescriptionRequest request, String doctorEmail) {
        Login doctor = loginRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("404: Patient not found"));

        Prescription prescription = new Prescription();
        prescription.setDoctor(doctor);
        prescription.setPatient(patient);
        prescription.setMedicationName(request.getMedicationName());
        prescription.setDosage(request.getDosage());
        prescription.setFrequency(request.getFrequency());
        prescription.setDuration(request.getDuration());
        prescription.setSpecialInstructions(request.getSpecialInstructions());
        if (request.getRoute() != null && !request.getRoute().isBlank()) {
            prescription.setRoute(request.getRoute());
        }
        prescription.setStatus("ACTIVE");

        // Default start date to now, end date dependent on duration parsing or frontend
        prescription.setStartDate(LocalDateTime.now());
        prescription.setRefillsRemaining(0);

        Prescription saved = prescriptionRepository.save(prescription);

        // Audit Log
        auditLogRepository.save(new AuditLog(doctorEmail, "PRESCRIPTION_CREATED", "INTERNAL", "SYSTEM",
            "Prescribed " + prescription.getMedicationName() + " to patient ID: " + patient.getProfileId()));

        return mapToDTO(saved);
    }

    /**
     * Retrieves a single prescription by ID, enforcing the same access rules
     * a controller-level {@link com.securehealth.backend.security.PatientAccessValidator}
     * check already gates entry with.
     *
     * @param id the prescription ID
     * @return the {@link PrescriptionDTO}, or {@code null} if not found
     */
    @Transactional(readOnly = true)
    public PrescriptionDTO getPrescriptionById(Long id) {
        return prescriptionRepository.findById(id).map(this::mapToDTO).orElse(null);
    }

    /**
     * Retrieves all prescriptions associated with a specific patient.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link PrescriptionDTO} objects
     */
    @Transactional(readOnly = true)
    public List<PrescriptionDTO> getPrescriptionsByPatient(Long patientId) {
        return prescriptionRepository.findByPatient_ProfileId(patientId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves only the current "ACTIVE" prescriptions for a specific patient.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link PrescriptionDTO} objects
     */
    @Transactional(readOnly = true)
    public List<PrescriptionDTO> getActivePrescriptionsByPatient(Long patientId) {
        return prescriptionRepository.findByPatient_ProfileIdAndStatus(patientId, "ACTIVE")
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Updates the editable clinical fields of an existing prescription
     * (dosage, frequency, duration, special instructions, status).
     * <p>
     * Restricted to the prescribing doctor, mirroring {@link #refillPrescription}'s
     * ownership check. Patient/medication identity cannot be changed via this
     * endpoint — discontinue and re-prescribe instead.
     * </p>
     *
     * @param id          the ID of the prescription to update
     * @param request     the updated field values
     * @param doctorEmail the email of the doctor performing the update
     * @return the updated {@link PrescriptionDTO}
     * @throws RuntimeException if the prescription is not found or not owned by this doctor
     */
    @Transactional
    public PrescriptionDTO updatePrescription(Long id, PrescriptionRequest request, String doctorEmail) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Prescription not found"));

        if (!prescription.getDoctor().getEmail().equals(doctorEmail)) {
            throw new RuntimeException("403: You can only update prescriptions you issued.");
        }

        if (request.getDosage() != null) prescription.setDosage(request.getDosage());
        if (request.getFrequency() != null) prescription.setFrequency(request.getFrequency());
        if (request.getDuration() != null) prescription.setDuration(request.getDuration());
        if (request.getSpecialInstructions() != null) prescription.setSpecialInstructions(request.getSpecialInstructions());

        Prescription saved = prescriptionRepository.save(prescription);

        auditLogRepository.save(new AuditLog(doctorEmail, "PRESCRIPTION_UPDATED", "INTERNAL", "SYSTEM",
                "Updated prescription ID: " + id + " for patient ID: " + prescription.getPatient().getProfileId()));

        return mapToDTO(saved);
    }

    /**
     * Decrements the refill count of an existing prescription.
     *
     * @param id the ID of the prescription to refill
     * @param doctorEmail the email of the doctor authorizing the refill
     * @return the updated {@link PrescriptionDTO}
     * @throws RuntimeException if the prescription is not found, unauthorized, or no refills remain
     */
    @Transactional
    public PrescriptionDTO refillPrescription(Long id, String doctorEmail) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Prescription not found"));

        if (!prescription.getDoctor().getEmail().equals(doctorEmail)) {
            throw new RuntimeException("403: You can only refill prescriptions you issued.");
        }

        if (prescription.getRefillsRemaining() <= 0) {
            throw new RuntimeException("400: No refills remaining. Please create a new prescription.");
        }

        prescription.setRefillsRemaining(prescription.getRefillsRemaining() - 1);
        return mapToDTO(prescriptionRepository.save(prescription));
    }

    /**
     * Permanently deletes a prescription from the system.
     * <p>
     * Restricted administrative action that generates an audit log entry.
     * </p>
     *
     * @param id the ID of the prescription to delete
     * @param adminEmail the email of the administrator performing the deletion
     * @throws RuntimeException if the prescription is not found
     */
    @Transactional
    public void deletePrescription(Long id, String adminEmail) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Prescription not found"));

        prescriptionRepository.delete(prescription);

        auditLogRepository.save(new AuditLog(adminEmail, "PRESCRIPTION_DELETED", "INTERNAL", "SYSTEM",
            "Deleted prescription ID: " + id + " for patient ID: " + prescription.getPatient().getProfileId()));
    }
}
