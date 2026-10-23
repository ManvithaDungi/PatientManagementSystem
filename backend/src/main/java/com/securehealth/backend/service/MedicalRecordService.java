package com.securehealth.backend.service;

import com.securehealth.backend.dto.MedicalRecordDTO;
import com.securehealth.backend.dto.MedicalRecordRequest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.MedicalRecord;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.MedicalRecordRepository;
import com.securehealth.backend.repository.PatientProfileRepository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing patients' clinical medical records.
 * <p>
 * Handles the creation of encounter records by doctors, chronological 
 * retrieval for patients, and secure administrative deletion with auditing.
 * </p>
 */
@Service
public class MedicalRecordService {

    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private com.securehealth.backend.security.AccessPolicy accessPolicy;
    @Autowired private com.securehealth.backend.repository.UploadedFileRepository uploadedFileRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private com.securehealth.backend.repository.DoctorProfileRepository doctorProfileRepository;

    /** Resolves a display name for a doctor; falls back to email if no profile exists. */
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
     * Maps a {@link MedicalRecord} entity to its API-safe {@link MedicalRecordDTO}.
     * <p>
     * Every read/write path returns through this mapper — never the raw entity,
     * since {@code MedicalRecord.doctor}/{@code .patient} carry {@code Login}
     * with no JSON guard on {@code passwordHash}/{@code otp}.
     * </p>
     */
    private MedicalRecordDTO mapToDTO(MedicalRecord mr) {
        MedicalRecordDTO dto = new MedicalRecordDTO();
        dto.setRecordId(mr.getRecordId());
        dto.setPatientId(mr.getPatient().getProfileId());
        dto.setDoctorName(resolveDoctorName(mr.getDoctor()));
        dto.setDiagnosis(mr.getDiagnosis());
        dto.setSymptoms(mr.getSymptoms());
        dto.setTreatmentProvided(mr.getTreatmentProvided());
        dto.setNotes(mr.getNotes());
        dto.setAttachmentUrl(mr.getAttachmentUrl());
        dto.setRecordDate(mr.getCreatedAt());
        dto.setCreatedAt(mr.getCreatedAt());
        return dto;
    }

    /**
     * Creates a new clinical medical record for a patient.
     * <p>
     * Automatically generates an audit log entry upon successful creation.
     * </p>
     *
     * @param request the {@link MedicalRecordRequest} details
     * @param doctorEmail the email of the attending doctor
     * @return the saved record, as a {@link MedicalRecordDTO}
     */
    @Transactional
    public MedicalRecordDTO createMedicalRecord(MedicalRecordRequest request, String doctorEmail) {
        Login doctor = loginRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Doctor not found"));

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Patient not found"));

        accessPolicy.checkWrite(patient.getProfileId(), "DOCTOR", doctorEmail, "MEDICAL_RECORDS");

        MedicalRecord record = new MedicalRecord();
        record.setDoctor(doctor);
        record.setPatient(patient);
        record.setDiagnosis(request.getDiagnosis());
        record.setSymptoms(request.getSymptoms());
        record.setTreatmentProvided(request.getTreatmentProvided());
        record.setNotes(request.getNotes());
        attach(record, request.getAttachmentUrl(), doctorEmail);

        MedicalRecord saved = medicalRecordRepository.save(record);

        // Audit Log
        auditLogRepository.save(new AuditLog(doctorEmail, "MEDICAL_RECORD_CREATED", "INTERNAL", "SYSTEM",
            "Created medical record id=" + saved.getRecordId() + " for patient ID: " + patient.getProfileId()));

        return mapToDTO(saved);
    }

    /**
     * Retrieves a single medical record by ID.
     *
     * @param id the record ID
     * @return the {@link MedicalRecordDTO}, or {@code null} if not found
     */
    /** Only a file the same doctor uploaded can be attached; a blank value clears the attachment. */
    private void attach(MedicalRecord record, String filename, String doctorEmail) {
        if (filename == null || filename.isBlank()) {
            record.setAttachmentUrl(null);
            return;
        }
        boolean own = uploadedFileRepository.findByFilename(filename)
                .map(f -> f.getUploadedBy() != null && doctorEmail.equals(f.getUploadedBy().getEmail()))
                .orElse(false);
        if (!own) {
            throw new com.securehealth.backend.exception.BadRequestException("The attached file was not uploaded by you.");
        }
        record.setAttachmentUrl(filename);
    }

    @Transactional(readOnly = true)
    public MedicalRecordDTO getMedicalRecordById(Long id) {
        return medicalRecordRepository.findById(id).map(this::mapToDTO).orElse(null);
    }

    /**
     * Retrieves all medical records associated with a specific patient.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link MedicalRecordDTO} objects
     */
    @Transactional(readOnly = true)
    public List<MedicalRecordDTO> getMedicalRecordsByPatient(Long patientId) {
        return medicalRecordRepository.findByPatient_ProfileId(patientId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Updates the clinical fields of an existing medical record.
     * <p>
     * Restricted to the authoring doctor.
     * </p>
     *
     * @param id          the ID of the record to update
     * @param request     the updated field values
     * @param doctorEmail the email of the doctor performing the update
     * @return the updated {@link MedicalRecordDTO}
     * @throws RuntimeException if the record is not found or not owned by this doctor
     */
    @Transactional
    public MedicalRecordDTO updateMedicalRecord(Long id, MedicalRecordRequest request, String doctorEmail) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Medical Record not found"));

        if (!record.getDoctor().getEmail().equals(doctorEmail)) {
            throw new com.securehealth.backend.exception.ForbiddenException("403: You can only update medical records you authored.");
        }

        Limits.max("diagnosis", request.getDiagnosis(), 2000);
        if (request.getDiagnosis() != null) record.setDiagnosis(request.getDiagnosis());
        if (request.getSymptoms() != null) record.setSymptoms(request.getSymptoms());
        if (request.getTreatmentProvided() != null) record.setTreatmentProvided(request.getTreatmentProvided());
        if (request.getNotes() != null) record.setNotes(request.getNotes());
        if (request.getAttachmentUrl() != null) attach(record, request.getAttachmentUrl(), doctorEmail);

        MedicalRecord saved = medicalRecordRepository.save(record);

        auditLogRepository.save(new AuditLog(doctorEmail, "MEDICAL_RECORD_UPDATED", "INTERNAL", "SYSTEM",
                "Updated medical record ID: " + id + " for patient ID: " + record.getPatient().getProfileId()));

        return mapToDTO(saved);
    }

    /**
     * Deletes a specific medical record.
     * <p>
     * Restricted operation that triggers an audit log entry for accountability.
     * </p>
     *
     * @param id the ID of the medical record to delete
     * @param adminEmail the email of the administrator performing the deletion
     * @throws RuntimeException if the record is not found
     */
    @Transactional
    public void deleteMedicalRecord(Long id, String adminEmail) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Medical Record not found"));
        
        medicalRecordRepository.delete(record);

        auditLogRepository.save(new AuditLog(adminEmail, "MEDICAL_RECORD_DELETED", "INTERNAL", "SYSTEM", 
            "Deleted medical record ID: " + id + " for patient ID: " + record.getPatient().getProfileId()));
    }
}