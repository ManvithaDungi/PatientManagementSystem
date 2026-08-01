package com.securehealth.backend.controller;

import com.securehealth.backend.model.VitalSign;
import com.securehealth.backend.repository.VitalSignRepository;
import com.securehealth.backend.service.VitalSignService;
import com.securehealth.backend.security.PatientAccessValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


import java.util.List;

/**
 * REST controller for managing patient vital signs.
 * <p>
 * Provides endpoints for retrieving vital signs history, getting the latest readings,
 * and recording new vital signs (restricted to authorized personnel).
 * </p>
 */
@RestController
@RequestMapping("/api/vital-signs")
public class VitalSignController {

    @Autowired private VitalSignRepository vitalSignRepository;
    @Autowired private PatientAccessValidator accessValidator;
    @Autowired private VitalSignService vitalSignService;

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getByPatient(@PathVariable Long patientId, Authentication auth) {
        accessValidator.validateAccess(patientId, auth, "VITAL_SIGNS");
        return ResponseEntity.ok(vitalSignService.getVitalSignsByPatient(patientId));
    }

    @GetMapping("/patient/{patientId}/latest")
    public ResponseEntity<?> getLatestByPatient(@PathVariable Long patientId, Authentication auth) {
        accessValidator.validateAccess(patientId, auth, "VITAL_SIGNS");
        try {
            return ResponseEntity.ok(vitalSignService.getLatestVitalSignByPatient(patientId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN', 'NURSE')")
    public ResponseEntity<?> createVitalSign(@Valid @RequestBody com.securehealth.backend.dto.VitalSignRequest request, Authentication auth) {
        try {
            VitalSign newVital = vitalSignService.createVitalSign(request, auth.getName());
            return ResponseEntity.ok(newVital);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteVitalSign(@PathVariable Long id) {
        try {
            vitalSignService.deleteVitalSign(id);
            return ResponseEntity.ok("Vital sign deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}