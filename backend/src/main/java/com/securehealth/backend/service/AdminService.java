package com.securehealth.backend.service;

import com.securehealth.backend.dto.AdminMetricsDTO;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.dto.StaffDTO;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.securehealth.backend.dto.PatientDirectoryDTO;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service for administrative and management operations.
 * <p>
 * Provides high-level system metrics for the dashboard, staff account management 
 * (roles and removal), and access to the full patient directory.
 * </p>
 */
@Service
public class AdminService {

    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private com.securehealth.backend.repository.AuditLogRepository auditLogRepository;
    @Autowired private AuditService auditService;
    @Autowired private com.securehealth.backend.repository.SessionRepository sessionRepository;
    @Autowired private com.securehealth.backend.repository.DoctorProfileRepository doctorProfileRepository;
    @Autowired private TokenBlacklistService tokenBlacklistService;

    /**
     * Retrieves aggregated system metrics for the admin dashboard.
     *
     * @return an {@link AdminMetricsDTO} containing counts of patients, doctors, 
     *         pending approvals, and today's appointments
     */
    @Transactional(readOnly = true)
    public AdminMetricsDTO getDashboardMetrics() {
        AdminMetricsDTO metrics = new AdminMetricsDTO();

        // 1. Total Patients
        metrics.setTotalPatients(patientProfileRepository.count());

        // 2. Total Doctors
        metrics.setTotalDoctors(loginRepository.countByRole(Role.DOCTOR));

        // 3. Pending Approvals
        metrics.setPendingApprovals(appointmentRepository.countByStatus(AppointmentStatus.PENDING_APPROVAL));

        // 4. Today's Appointments
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        metrics.setTodaysAppointments(appointmentRepository.countTodaysAppointments(startOfDay, endOfDay));

        return metrics;
    }

    /**
     * Retrieves a list of all non-patient staff members.
     *
     * @return a list of {@link StaffDTO} objects representing system staff
     */
    @Transactional(readOnly = true)
    public List<StaffDTO> getAllStaff() {
        // Pass the Enum to the repository, not the string "PATIENT"
        return loginRepository.findByRoleNot(Role.PATIENT).stream().map(user -> {
            StaffDTO dto = new StaffDTO();
            dto.setUserId(user.getUserId()); 
            dto.setEmail(user.getEmail());
            // Fix Line 54: Convert Enum to String for the DTO
            dto.setRole(user.getRole().name()); 
            dto.setEnabled(!Boolean.TRUE.equals(user.getArchived()));
            dto.setLastLoginAt(user.getLastLoginAt());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Updates the role of a staff member.
     *
     * @param userId the ID of the user to update
     * @param newRole the new role to assign
     * @return the updated {@link StaffDTO}
     * @throws IllegalArgumentException if the user is not found or is a patient
     */
    @Transactional
    public StaffDTO updateStaffRole(Long userId, String newRole, String adminEmail) {
        Login user = loginRepository.findById(userId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Staff member not found."));
        if (user.getRole() == Role.PATIENT) {
            throw new com.securehealth.backend.exception.BadRequestException("Patient accounts cannot be managed through staff management.");
        }
        Role target;
        try {
            target = Role.valueOf(newRole == null ? "" : newRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.securehealth.backend.exception.BadRequestException("Unsupported role.");
        }
        if (target == Role.PATIENT) {
            throw new com.securehealth.backend.exception.BadRequestException("Staff cannot be converted into patients.");
        }
        if (adminEmail != null && adminEmail.equalsIgnoreCase(user.getEmail())) {
            throw new com.securehealth.backend.exception.BadRequestException("You cannot change your own role.");
        }
        if (user.getRole() == Role.ADMIN && target != Role.ADMIN
                && loginRepository.countActiveByRole(Role.ADMIN) <= 1) {
            throw new com.securehealth.backend.exception.BadRequestException("The last administrator cannot be demoted.");
        }
        Role previous = user.getRole();
        user.setRole(target);
        if (target == Role.ADMIN || target == Role.DOCTOR) {
            user.setTwoFactorEnabled(true);
        }
        loginRepository.save(user);
        if (target == Role.DOCTOR && doctorProfileRepository.findByUser(user).isEmpty()) {
            com.securehealth.backend.model.DoctorProfile profile = new com.securehealth.backend.model.DoctorProfile();
            profile.setUser(user);
            profile.setFirstName("New");
            profile.setLastName("Doctor");
            profile.setSpecialty("General");
            doctorProfileRepository.save(profile);
        }
        if (adminEmail != null) {
            auditService.record(adminEmail, "STAFF_ROLE_CHANGED", "user=" + userId + " " + previous + "->" + target);
        }

        StaffDTO dto = new StaffDTO();
        dto.setUserId(user.getUserId());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());
        return dto;
    }

    /** Soft-disables a staff account (no data is deleted) and ends all of its sessions. */
    @Transactional
    public void removeStaffMember(Long userId, String adminEmail) {
        Login user = loginRepository.findById(userId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Staff member not found."));
        if (user.getRole() == Role.PATIENT) {
            throw new com.securehealth.backend.exception.BadRequestException("Patient accounts cannot be removed through staff management.");
        }
        if (adminEmail != null && adminEmail.equalsIgnoreCase(user.getEmail())) {
            throw new com.securehealth.backend.exception.BadRequestException("You cannot remove your own account.");
        }
        if (user.getRole() == Role.ADMIN && loginRepository.countActiveByRole(Role.ADMIN) <= 1) {
            throw new com.securehealth.backend.exception.BadRequestException("The last administrator cannot be removed.");
        }
        user.setArchived(true);
        loginRepository.save(user);
        for (com.securehealth.backend.model.Session active : sessionRepository.findActiveSessionsByUserOrderByCreatedAtAsc(user)) {
            tokenBlacklistService.clearIdleSession("s:" + active.getId());
        }
        sessionRepository.revokeAllUserSessions(user.getUserId());
        tokenBlacklistService.clearIdleSession(user.getEmail());
        if (adminEmail != null) {
            auditService.record(adminEmail, "STAFF_DISABLED", "user=" + userId + " role=" + user.getRole());
        }
    }

    @Transactional
    public void enableStaffMember(Long userId, String adminEmail) {
        Login user = loginRepository.findById(userId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Staff member not found."));
        if (user.getRole() == Role.PATIENT) {
            throw new com.securehealth.backend.exception.BadRequestException("Patient accounts cannot be managed through staff management.");
        }
        user.setArchived(false);
        loginRepository.save(user);
        if (adminEmail != null) {
            auditService.record(adminEmail, "STAFF_ENABLED", "user=" + userId);
        }
    }

    @Transactional(readOnly = true)
    public List<PatientDirectoryDTO> getAllPatients() {
        return patientProfileRepository.findAll(PageParams.of(0, PageParams.DEFAULT_SIZE,
                org.springframework.data.domain.Sort.by("lastName", "firstName"))).getContent().stream().map(patient -> {
            PatientDirectoryDTO dto = new PatientDirectoryDTO();
            dto.setProfileId(patient.getProfileId());
            dto.setFirstName(patient.getFirstName());
            dto.setLastName(patient.getLastName());
            dto.setContactNumber(patient.getContactNumber());
            dto.setDateOfBirth(patient.getDateOfBirth());
            if (patient.getAssignedDoctor() != null) {
                dto.setAssignedDoctorId(patient.getAssignedDoctor().getUserId());
            }
            if (patient.getAssignedNurse() != null) {
                dto.setAssignedNurseId(patient.getAssignedNurse().getUserId());
            }
            
            // Safely grab the email from the linked Login entity (if they have an account)
            if (patient.getUser() != null) {
                dto.setUserId(patient.getUser().getUserId());
                dto.setEmail(patient.getUser().getEmail());
            } else {
                dto.setEmail("No registered account");
            }
            
            return dto;
        }).collect(Collectors.toList());
    }

    /** Assigns (or clears, when an id is null) a patient's treating doctor and nurse. */
    @Transactional
    public com.securehealth.backend.dto.CareTeamDTO updateCareTeam(Long patientProfileId, Long doctorUserId,
                                                                   Long nurseUserId, String adminEmail) {
        com.securehealth.backend.model.PatientProfile patient = patientProfileRepository.findById(patientProfileId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Patient not found."));
        patient.setAssignedDoctor(resolveStaff(doctorUserId, Role.DOCTOR));
        patient.setAssignedNurse(resolveStaff(nurseUserId, Role.NURSE));
        patientProfileRepository.save(patient);
        auditLogRepository.save(new com.securehealth.backend.model.AuditLog(adminEmail, "CARE_TEAM_UPDATED",
                "INTERNAL", "SYSTEM", "Patient " + patientProfileId + " doctor=" + doctorUserId + " nurse=" + nurseUserId));
        return new com.securehealth.backend.dto.CareTeamDTO(patientProfileId, doctorUserId, nurseUserId);
    }

    private Login resolveStaff(Long userId, Role expected) {
        if (userId == null) return null;
        Login user = loginRepository.findById(userId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Staff member not found."));
        if (user.getRole() != expected) {
            throw new com.securehealth.backend.exception.BadRequestException("The selected user is not a " + expected.name().toLowerCase() + ".");
        }
        return user;
    }
}
