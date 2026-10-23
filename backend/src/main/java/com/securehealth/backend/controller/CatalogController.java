package com.securehealth.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for providing static or mock catalog data.
 * <p>
 * This controller exposes endpoints for frontend dropdowns, such as medications, 
 * test types, prescription protocols, conditions, and hospital departments.
 * </p>
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    @GetMapping("/medications")
    public ResponseEntity<?> getMedications() {
        return ResponseEntity.ok(List.of(
                Map.of("id", 1, "name", "Amoxicillin"),
                Map.of("id", 2, "name", "Lisinopril"),
                Map.of("id", 3, "name", "Atorvastatin"),
                Map.of("id", 4, "name", "Metformin"),
                Map.of("id", 5, "name", "Ibuprofen")
        ));
    }

    @GetMapping("/test-types")
    public ResponseEntity<?> getTestTypes() {
        return ResponseEntity.ok(List.of(
                Map.of("id", 1, "name", "Complete Blood Count (CBC)"),
                Map.of("id", 2, "name", "Basic Metabolic Panel (BMP)"),
                Map.of("id", 3, "name", "Lipid Panel"),
                Map.of("id", 4, "name", "Urinalysis"),
                Map.of("id", 5, "name", "HbA1C")
        ));
    }

    @GetMapping("/prescription-protocols")
    public ResponseEntity<?> getPrescriptionProtocols() {
        return ResponseEntity.ok(List.of(
                Map.of("id", 1, "name", "Standard Antibiotic Protocol"),
                Map.of("id", 2, "name", "Hypertension Maintenance"),
                Map.of("id", 3, "name", "Diabetes Type 2 Starter")
        ));
    }

    @GetMapping("/conditions")
    public ResponseEntity<?> getConditions() {
        return ResponseEntity.ok(List.of(
                Map.of("id", 1, "name", "Hypertension"),
                Map.of("id", 2, "name", "Type 2 Diabetes"),
                Map.of("id", 3, "name", "Hyperlipidemia"),
                Map.of("id", 4, "name", "Asthma")
        ));
    }

    @GetMapping("/hospital-departments")
    public ResponseEntity<?> getHospitalDepartments() {
        return ResponseEntity.ok(List.of(
                Map.of("id", 1, "name", "Cardiology"),
                Map.of("id", 2, "name", "Neurology"),
                Map.of("id", 3, "name", "Pediatrics"),
                Map.of("id", 4, "name", "Orthopedics"),
                Map.of("id", 5, "name", "General Practice")
        ));
    }
}
