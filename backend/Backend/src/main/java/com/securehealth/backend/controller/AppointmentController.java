package com.securehealth.backend.controller;

import com.securehealth.backend.dto.AppointmentDTO;
import com.securehealth.backend.dto.AppointmentRequest;
import com.securehealth.backend.model.Appointment;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import com.securehealth.backend.service.AppointmentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PatientAccessValidator accessValidator;
    @Autowired private AppointmentService appointmentService;
    @Autowired private LoginRepository loginRepository;

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<AppointmentDTO>> getByPatient(@PathVariable Long patientId, Authentication auth) {
        accessValidator.validateAccess(patientId, auth);
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatient(patientId));
    }

    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public ResponseEntity<?> getByDoctor(@PathVariable Long doctorId, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"));
        if (!isAdmin && !auth.getName().equals(doctorLoginEmail(doctorId))) {
            return ResponseEntity.status(403).body("Forbidden: You can only view your own appointment schedule.");
        }
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctor(doctorId));
    }

    private String doctorLoginEmail(Long doctorId) {
        return loginRepository.findById(doctorId).map(Login::getEmail).orElse(null);
    }

    // GET /api/appointments/doctor/{doctorId}/available-slots?date=2026-03-01
    @GetMapping("/doctor/{doctorId}/available-slots")
    public ResponseEntity<List<LocalTime>> getAvailableSlots(
            @PathVariable Long doctorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<LocalTime> slots = appointmentService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(slots);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PATIENT')")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody AppointmentRequest request, Authentication auth) {
        try {
            // Extract the email from the JWT token
            String email = auth.getName();

            AppointmentDTO newAppointment = appointmentService.createAppointment(request, email);

            return ResponseEntity.ok(newAppointment);

        } catch (RuntimeException e) {
            // If it's our double-booking error, return a 409 Conflict. Otherwise, 400 Bad Request.
            if (e.getMessage().contains("409")) {
                return ResponseEntity.status(409).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approveAppointment(@PathVariable Long id, Authentication auth) {
        // Mock-safe security check for unit tests
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
        if (!isAdmin) {
            return ResponseEntity.status(403).body("Forbidden: Only administrative staff can approve appointments.");
        }

        try {
            return ResponseEntity.ok(appointmentService.approveAppointment(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> rejectAppointment(@PathVariable Long id, @RequestBody(required = false) String reason, Authentication auth) {
        // Mock-safe security check for unit tests
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
        if (!isAdmin) {
            return ResponseEntity.status(403).body("Forbidden: Only administrative staff can approve appointments.");
        }

        try {
            return ResponseEntity.ok(appointmentService.rejectAppointment(id, reason));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public ResponseEntity<?> getAllAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getPendingAppointments() {
        return ResponseEntity.ok(appointmentService.getPendingAppointments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication auth) {
        // Resolved via the repository only to run the access check against the owning
        // patient; the response is always the DTO, never the raw entity (whose .doctor
        // association carries Login with no JSON guard on passwordHash/otp).
        Appointment appt = appointmentRepository.findById(id).orElse(null);
        if (appt == null) {
            return ResponseEntity.status(404).body("Appointment not found with id: " + id);
        }
        try {
            accessValidator.validateAccess(appt.getPatient().getProfileId(), auth);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<AppointmentDTO>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByStatus(AppointmentStatus.valueOf(status.toUpperCase())));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR')")
    public ResponseEntity<?> getStats(Authentication auth) {
        boolean allowed = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("DOCTOR"));
        if (!allowed) {
            return ResponseEntity.status(403).body("Forbidden: Only doctors and admins can view appointment stats.");
        }
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", appointmentRepository.count());
        stats.put("pending", appointmentRepository.countByStatus(AppointmentStatus.PENDING_APPROVAL));
        stats.put("scheduled", appointmentRepository.countByStatus(AppointmentStatus.SCHEDULED));
        stats.put("completed", appointmentRepository.countByStatus(AppointmentStatus.COMPLETED));
        stats.put("cancelled", appointmentRepository.countByStatus(AppointmentStatus.CANCELLED));
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('DOCTOR')")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id, Authentication auth) {
        try {
            return ResponseEntity.ok(appointmentService.completeAppointment(id, auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCTOR')")
    public ResponseEntity<?> updateAppointment(
            @PathVariable Long id, 
            @RequestBody com.securehealth.backend.dto.AppointmentDTO request, 
            Authentication auth) {
        try {
            return ResponseEntity.ok(appointmentService.updateAppointment(id, request, auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DOCTOR', 'PATIENT')")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body, Authentication auth) {
        try {
            String role = auth.getAuthorities().stream().findFirst().get().getAuthority();
            String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
            return ResponseEntity.ok(appointmentService.cancelAppointment(id, auth.getName(), role, reason));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    /**
     * PATIENT ONLY: Reschedules the caller's own appointment to a new date/time.
     * Unlike {@code PUT /{id}} (DOCTOR-only, full update), this only accepts a new
     * date and resets the appointment to PENDING_APPROVAL for re-confirmation.
     * Endpoint: PUT /api/appointments/{id}/reschedule
     */
    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('PATIENT')")
    public ResponseEntity<?> rescheduleAppointment(@PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body, Authentication auth) {
        try {
            Object rawDate = body.get("appointmentDate");
            if (rawDate == null) {
                return ResponseEntity.badRequest().body("appointmentDate is required");
            }
            java.time.LocalDateTime newDate = java.time.LocalDateTime.parse(String.valueOf(rawDate));
            return ResponseEntity.ok(appointmentService.rescheduleAppointment(id, newDate, auth.getName()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                return ResponseEntity.status(409).body(e.getMessage());
            }
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteAppointment(@PathVariable Long id) {
        try {
            appointmentService.deleteAppointment(id);
            return ResponseEntity.ok("Appointment deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}