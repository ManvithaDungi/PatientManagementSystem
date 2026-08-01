package com.securehealth.backend.controller;

import com.securehealth.backend.model.MedicalRecord;
import com.securehealth.backend.dto.MedicalRecordDTO;
import com.securehealth.backend.repository.MedicalRecordRepository;
import com.securehealth.backend.service.MedicalRecordService;
import com.securehealth.backend.security.PatientAccessValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing medical records.
 * <p>
 * Provides endpoints for retrieving medical records by patient, creating new records
 * (restricted to doctors), and deleting records (restricted to admins).
 * </p>
 */
@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {

    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private PatientAccessValidator accessValidator;
    @Autowired private MedicalRecordService medicalRecordService;

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalRecordDTO>> getByPatient(@PathVariable Long patientId, Authentication auth) {
        accessValidator.validateAccess(patientId, auth, "MEDICAL_RECORDS");
        return ResponseEntity.ok(medicalRecordService.getMedicalRecordsByPatient(patientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication auth) {
        MedicalRecord record = medicalRecordRepository.findById(id).orElse(null);
        if (record == null) {
            return ResponseEntity.status(404).body("Medical record not found with id: " + id);
        }
        try {
            accessValidator.validateAccess(record.getPatient().getProfileId(), auth, "MEDICAL_RECORDS");
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
        return ResponseEntity.ok((Object) record);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DOCTOR')")
    public ResponseEntity<?> createMedicalRecord(@Valid @RequestBody com.securehealth.backend.dto.MedicalRecordRequest request, Authentication auth) {
        try {
            MedicalRecord newRecord = medicalRecordService.createMedicalRecord(request, auth.getName());
            return ResponseEntity.ok(newRecord);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteMedicalRecord(@PathVariable Long id, Authentication auth) {
        try {
            medicalRecordService.deleteMedicalRecord(id, auth.getName());
            return ResponseEntity.ok("Medical record deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}