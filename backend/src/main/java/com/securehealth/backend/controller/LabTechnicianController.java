package com.securehealth.backend.controller;

import com.securehealth.backend.dto.LabTestDTO;
import com.securehealth.backend.service.LabTechnicianService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for lab technician dashboard and order management.
 * <p>
 * Provides endpoints for lab technicians to view their dashboard, manage test orders,
 * update order status, and upload results. Access is restricted to users with LAB_TECHNICIAN authority.
 * </p>
 */
@RestController
@RequestMapping("/api/lab-technician")
@PreAuthorize("hasAuthority('LAB_TECHNICIAN')")
public class LabTechnicianController {

    @Autowired
    private LabTechnicianService labTechnicianService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardOverview() {
        try {
            return ResponseEntity.ok(labTechnicianService.getDashboardOverview());
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(@RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(labTechnicianService.getAllOrders(status));
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{testId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long testId,
            @RequestBody Map<String, String> payload,
            org.springframework.security.core.Authentication auth) {
        try {
            String newStatus = payload.get("status");
            if (newStatus == null || newStatus.isEmpty()) {
                return ResponseEntity.badRequest().body("Status is required");
            }
            LabTestDTO updatedTest = labTechnicianService.updateOrderStatus(testId, newStatus, auth.getName());
            return ResponseEntity.ok(updatedTest);
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{testId}/upload")
    public ResponseEntity<?> uploadResults(
            @PathVariable Long testId,
            @RequestBody Map<String, String> payload,
            org.springframework.security.core.Authentication auth) {
        try {
            String resultValue = payload.get("resultValue");
            String remarks = payload.get("remarks");
            String fileUrl = payload.get("fileUrl");

            if (resultValue == null || resultValue.isEmpty()) {
                return ResponseEntity.badRequest().body("Result value is required");
            }

            LabTestDTO completedTest = labTechnicianService.uploadResults(testId, resultValue, remarks, fileUrl,
                    payload.get("unit"), payload.get("referenceRange"), auth.getName());
            return ResponseEntity.ok(completedTest);
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
