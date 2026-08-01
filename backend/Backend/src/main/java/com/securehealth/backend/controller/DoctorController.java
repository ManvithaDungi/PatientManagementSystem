package com.securehealth.backend.controller;

import com.securehealth.backend.dto.DoctorDTO;
import com.securehealth.backend.service.DoctorService;
import com.securehealth.backend.service.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing doctor-related operations.
 * <p>
 * Provides endpoints for retrieving lists of doctors, searching by specialty or department,
 * updating doctor profiles, and retrieving lists of patients assigned to a doctor.
 * </p>
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private PatientService patientService;

    // Helper to extract email from security context
    private String getCurrentEmail(Authentication auth) {
        return auth.getName();
    }

    // Helper to extract role from security context
    private String getCurrentRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("UNKNOWN");
    }

    // Mirrors the @PreAuthorize check above each endpoint - see PatientController for why both exist.
    private boolean hasAnyAuthority(Authentication auth, String... allowed) {
        List<String> allowedList = List.of(allowed);
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(allowedList::contains);
    }

    @GetMapping
    public ResponseEntity<List<DoctorDTO>> getAllDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctors());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorDTO> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getDoctorById(id));
    }

    @GetMapping("/specialty/{specialty}")
    public ResponseEntity<List<DoctorDTO>> getDoctorsBySpecialty(@PathVariable String specialty) {
        return ResponseEntity.ok(doctorService.getDoctorsBySpecialty(specialty));
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<List<DoctorDTO>> getDoctorsByDepartment(@PathVariable String department) {
        return ResponseEntity.ok(doctorService.getDoctorsByDepartment(department));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public ResponseEntity<?> updateDoctorProfile(
            @PathVariable Long id,
            @RequestBody DoctorDTO doctorDTO,
            Authentication auth) {
        if (!hasAnyAuthority(auth, "DOCTOR", "ADMIN")) {
            return ResponseEntity.status(403).body("Forbidden: Only doctors and admins can update doctor profiles.");
        }
        return ResponseEntity.ok(doctorService.updateDoctorProfile(
                id,
                doctorDTO,
                getCurrentEmail(auth),
                getCurrentRole(auth)
        ));
    }

    @GetMapping("/{doctorId}/patients")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getPatientsByDoctor(
            @PathVariable Long doctorId,
            Authentication auth) {
        if (!hasAnyAuthority(auth, "DOCTOR", "ADMIN")) {
            return ResponseEntity.status(403).body("Forbidden: Only doctors and admins can view a doctor's patient list.");
        }
        return ResponseEntity.ok(patientService.getPatientsByDoctor(
                doctorId,
                getCurrentEmail(auth),
                getCurrentRole(auth)
        ));
    }
}