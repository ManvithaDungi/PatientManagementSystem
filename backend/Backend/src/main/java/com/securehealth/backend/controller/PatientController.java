package com.securehealth.backend.controller;

import com.securehealth.backend.dto.PatientDTO;
import com.securehealth.backend.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing patient profiles.
 * <p>
 * Provides endpoints for listing all patients (with pagination), retrieving own profile,
 * creating/updating patient information, and searching by ID.
 * </p>
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

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

    // Mirrors the @PreAuthorize check above each endpoint. @PreAuthorize relies on method-security
    // AOP being wired (it is, in the real app via SecurityConfig), but that wiring isn't present in
    // sliced controller unit tests, so this explicit check keeps behavior verifiable and defense-in-depth.
    private boolean hasAnyAuthority(Authentication auth, String... allowed) {
        List<String> allowedList = List.of(allowed);
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(allowedList::contains);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getAllPatients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        if (!hasAnyAuthority(auth, "DOCTOR", "ADMIN")) {
            return ResponseEntity.status(403).body("Forbidden: Only doctors and admins can list patients.");
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(patientService.getAllPatients(getCurrentRole(auth), pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('PATIENT')")
    public ResponseEntity<?> getMyProfile(Authentication auth) {
        if (!hasAnyAuthority(auth, "PATIENT")) {
            return ResponseEntity.status(403).body("Forbidden: Only patients have a self profile.");
        }
        return ResponseEntity.ok(patientService.getPatientByEmail(getCurrentEmail(auth)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DOCTOR', 'ADMIN', 'PATIENT')")
    public ResponseEntity<?> getPatientById(@PathVariable Long id, Authentication auth) {
        if (!hasAnyAuthority(auth, "DOCTOR", "ADMIN", "PATIENT")) {
            return ResponseEntity.status(403).body("Forbidden: You do not have access to patient records.");
        }
        return ResponseEntity.ok(patientService.getPatientById(id, getCurrentEmail(auth), getCurrentRole(auth)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PATIENT')")
    public ResponseEntity<?> createPatient(@Valid @RequestBody PatientDTO patientDTO, Authentication auth) {
        if (!hasAnyAuthority(auth, "PATIENT")) {
            return ResponseEntity.status(403).body("Forbidden: Only patients can create their own profile.");
        }
        return ResponseEntity.ok(patientService.createPatientProfile(patientDTO, getCurrentEmail(auth)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PATIENT')")
    public ResponseEntity<?> updatePatient(@PathVariable Long id, @Valid @RequestBody PatientDTO patientDTO, Authentication auth) {
        if (!hasAnyAuthority(auth, "ADMIN", "PATIENT")) {
            return ResponseEntity.status(403).body("Forbidden: Only admins or the owning patient can update this profile.");
        }
        return ResponseEntity.ok(patientService.updatePatientProfile(id, patientDTO, getCurrentEmail(auth), getCurrentRole(auth)));
    }

    // Optional: Add DELETE if required by hospital policy, though typically records are deactivated, not deleted.
}