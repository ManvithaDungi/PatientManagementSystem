package com.securehealth.backend.controller;

import com.securehealth.backend.dto.AdminMetricsDTO;
import com.securehealth.backend.service.AdminInsightsService;
import com.securehealth.backend.service.AdminService;
import com.securehealth.backend.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for administrative operations.
 * <p>
 * This controller provides endpoints for managing system metrics, appointments, 
 * staff members, and patients. Access is restricted to users with ADMIN authority.
 * </p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AdminInsightsService insightsService;

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getDashboardMetrics() {
        AdminMetricsDTO metrics = adminService.getDashboardMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/appointments/pending")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getPendingAppointments() {
        return ResponseEntity.ok(appointmentService.getPendingAppointments());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllStaff() {
        return ResponseEntity.ok(adminService.getAllStaff());
    }

    @DeleteMapping("/staff/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> removeStaffMember(@PathVariable Long userId,
                                               org.springframework.security.core.Authentication auth) {
        adminService.removeStaffMember(userId, auth.getName());
        return ResponseEntity.ok(java.util.Map.of("message", "Staff member disabled."));
    }

    @PutMapping("/staff/{userId}/enable")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> enableStaffMember(@PathVariable Long userId,
                                               org.springframework.security.core.Authentication auth) {
        adminService.enableStaffMember(userId, auth.getName());
        return ResponseEntity.ok(java.util.Map.of("message", "Staff member enabled."));
    }

    @GetMapping("/patients")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllPatients() {
        return ResponseEntity.ok(adminService.getAllPatients());
    }

    @PutMapping("/staff/{userId}/role")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> updateStaffRole(
            @PathVariable Long userId,
            @RequestBody com.securehealth.backend.dto.RoleUpdateDTO request,
            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(adminService.updateStaffRole(userId, request.getNewRole(), auth.getName()));
    }

    @PutMapping("/patients/{patientId}/care-team")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> updateCareTeam(@PathVariable Long patientId,
                                            @RequestBody com.securehealth.backend.dto.CareTeamDTO request,
                                            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(adminService.updateCareTeam(
                patientId, request.getAssignedDoctorId(), request.getAssignedNurseId(), auth.getName()));
    }

    @GetMapping("/user-activity")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getUserActivity(@RequestParam(defaultValue = "24h") String timeframe) {
        return ResponseEntity.ok(insightsService.userActivity(timeframe));
    }

    /** Security-relevant audit entries: failed logins, lockouts, token theft, forbidden access... */
    @GetMapping("/security-events")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getSecurityEvents(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(insightsService.securityEvents(hours, limit));
    }

    /** Live database / Redis / JVM status. */
    @GetMapping("/system-health")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getSystemHealth() {
        return ResponseEntity.ok(insightsService.systemHealth());
    }
}
