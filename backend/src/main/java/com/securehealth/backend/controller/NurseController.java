package com.securehealth.backend.controller;

import com.securehealth.backend.service.NurseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for nurse-related operations.
 * <p>
 * Provides endpoints for nurses to view their dashboard, assigned patients, tasks,
 * and manage handover notes. Access is restricted to users with NURSE authority.
 * </p>
 */
@RestController
@RequestMapping("/api/nurse")
@PreAuthorize("hasAuthority('NURSE')")
public class NurseController {

    @Autowired
    private NurseService nurseService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardOverview(Authentication authentication) {
        try {
            return ResponseEntity.ok(nurseService.getDashboardOverview(authentication.getName()));
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/assigned-patients")
    @Transactional
    public ResponseEntity<?> getAssignedPatients(Authentication authentication) {
        try {
            return ResponseEntity.ok(com.securehealth.backend.dto.NurseViews.patients(nurseService.getAssignedPatients(authentication.getName())));
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/tasks")
    @Transactional
    public ResponseEntity<?> getTasks(Authentication authentication) {
        try {
            return ResponseEntity.ok(com.securehealth.backend.dto.NurseViews.tasks(nurseService.getTasks(authentication.getName())));
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/tasks/{taskId}/toggle")
    @Transactional
    public ResponseEntity<?> toggleTaskStatus(@PathVariable Long taskId, Authentication authentication) {
        try {
            java.util.Map<String, Object> result = nurseService.toggleTaskStatus(taskId, authentication.getName());
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>(result);
            if (result.get("task") instanceof com.securehealth.backend.model.NurseTask t) {
                body.put("task", com.securehealth.backend.dto.NurseViews.task(t));
            }
            return ResponseEntity.ok(body);
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/tasks")
    @Transactional
    public ResponseEntity<?> createTask(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            return ResponseEntity.ok(com.securehealth.backend.dto.NurseViews.task(nurseService.createTask(payload, authentication.getName())));
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/handover")
    @Transactional
    public ResponseEntity<?> getHandoverNotes(Authentication authentication) {
        try {
            java.util.Map<String, Object> notes = nurseService.getHandoverNotes(authentication.getName());
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, Object> e : notes.entrySet()) {
                @SuppressWarnings("unchecked")
                java.util.List<com.securehealth.backend.model.HandoverNote> list =
                        (java.util.List<com.securehealth.backend.model.HandoverNote>) e.getValue();
                body.put(e.getKey(), com.securehealth.backend.dto.NurseViews.notes(list));
            }
            return ResponseEntity.ok(body);
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/handover")
    @Transactional
    public ResponseEntity<?> saveHandoverNotes(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            return ResponseEntity.ok(com.securehealth.backend.dto.NurseViews.note(nurseService.saveHandoverNote(payload, authentication.getName())));
        } catch (com.securehealth.backend.exception.ForbiddenException | com.securehealth.backend.exception.NotFoundException | com.securehealth.backend.exception.ConflictException typed) {
            throw typed;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Records that the calling nurse administered a dose of a prescribed medication.
     * Endpoint: POST /api/nurse/medications/{prescriptionId}/administer
     */
    @PostMapping("/medications/{prescriptionId}/administer")
    public ResponseEntity<?> recordMedicationAdministration(
            @PathVariable Long prescriptionId,
            @RequestBody(required = false) Map<String, Object> payload,
            Authentication authentication) {
        String notes = payload != null && payload.get("notes") != null ? String.valueOf(payload.get("notes")) : null;
        return ResponseEntity.ok(nurseService.recordMedicationAdministration(prescriptionId, authentication.getName(), notes));
    }

    @GetMapping("/medications/{prescriptionId}/history")
    public ResponseEntity<?> getMedicationAdministrationHistory(
            @PathVariable Long prescriptionId, Authentication authentication) {
        return ResponseEntity.ok(nurseService.getAdministrationHistory(prescriptionId, authentication.getName()));
    }
}
