package com.securehealth.backend.controller;

import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.dto.LabTestDTO;
import com.securehealth.backend.repository.LabTestRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import com.securehealth.backend.service.LabTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing lab test results.
 * <p>
 * Provides endpoints for retrieving lab results by patient, creating new lab tests,
 * and listing pending tests for lab technicians.
 * </p>
 */
@RestController
@RequestMapping("/api/lab-results")
public class LabResultController {

    @Autowired private LabTestRepository labTestRepository;
    @Autowired private PatientAccessValidator accessValidator;
    @Autowired private LabTestService labTestService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<LabTestDTO>> getAllLabTests() {
        return ResponseEntity.ok(labTestService.getAllLabTests());
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<LabTestDTO>> getByPatient(@PathVariable Long patientId, Authentication auth) {
        accessValidator.validateAccess(patientId, auth, "LAB_RESULTS");
        return ResponseEntity.ok(labTestService.getLabTestsByPatient(patientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication auth) {
        // Resolved via the repository only to run the access check against the owning
        // patient; the response is always the DTO, never the raw entity (whose
        // .orderedBy/.patient associations carry Login with no JSON guard on passwordHash/otp).
        LabTest test = labTestRepository.findById(id).orElse(null);
        if (test == null) {
            return ResponseEntity.status(404).body("Lab result not found with id: " + id);
        }
        try {
            accessValidator.validateAccess(test.getPatient().getProfileId(), auth, "LAB_RESULTS");
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
        return ResponseEntity.ok(labTestService.getLabTestById(id));
    }



    @PostMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN', 'LAB_TECHNICIAN')")
    public ResponseEntity<?> createLabTest(@Valid @RequestBody com.securehealth.backend.dto.LabTestRequest request, Authentication auth) {
        try {
            LabTestDTO newLabTest = labTestService.createLabTest(request, auth.getName());
            return ResponseEntity.ok(newLabTest);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('LAB_TECHNICIAN', 'ADMIN', 'DOCTOR')")
    public ResponseEntity<List<LabTestDTO>> getPendingLabTests() {
        return ResponseEntity.ok(labTestService.getPendingLabTests());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteLabTest(@PathVariable Long id) {
        try {
            labTestService.deleteLabTest(id);
            return ResponseEntity.ok("Lab result deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}