package com.securehealth.backend.service;

import com.securehealth.backend.dto.AdminMetricsDTO;
import com.securehealth.backend.dto.StaffDTO;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private LoginRepository loginRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AuditService auditService;

    @InjectMocks private AdminService adminService;

    private Login mockNurse;

    @BeforeEach
    void setUp() {
        mockNurse = new Login();
        mockNurse.setUserId(1L);
        mockNurse.setEmail("nurse@hospital.com");
        mockNurse.setRole(Role.NURSE);
    }

    @Test
    void getDashboardMetrics_ReturnsAccurateCounts() {
        // Arrange
        when(patientProfileRepository.count()).thenReturn(150L);
        when(loginRepository.countByRole(Role.DOCTOR)).thenReturn(12L);
        when(appointmentRepository.countByStatus(AppointmentStatus.PENDING_APPROVAL)).thenReturn(5L);
        when(appointmentRepository.countTodaysAppointments(any(), any())).thenReturn(20L);

        // Act
        AdminMetricsDTO metrics = adminService.getDashboardMetrics();

        // Assert
        assertEquals(150L, metrics.getTotalPatients());
        assertEquals(12L, metrics.getTotalDoctors());
        assertEquals(5L, metrics.getPendingApprovals());
        assertEquals(20L, metrics.getTodaysAppointments());
    }

    @Test
    void getAllStaff_ReturnsOnlyStaffMembers() {
        // Arrange
        when(loginRepository.findByRoleNot(Role.PATIENT)).thenReturn(List.of(mockNurse));

        // Act
        List<StaffDTO> staff = adminService.getAllStaff();

        // Assert
        assertEquals(1, staff.size());
        assertEquals("nurse@hospital.com", staff.get(0).getEmail());
        assertEquals("NURSE", staff.get(0).getRole()); // DTO uses String
    }

    @Test
    void updateStaffRole_UpdatesEnumAndReturnsDto() {
        // Arrange
        when(loginRepository.findById(1L)).thenReturn(Optional.of(mockNurse));

        // Act
        StaffDTO updatedStaff = adminService.updateStaffRole(1L, "ADMIN", "root@x.test");

        // Assert
        assertEquals(Role.ADMIN, mockNurse.getRole()); // Entity should be updated
        assertEquals("ADMIN", updatedStaff.getRole()); // DTO should reflect new role
        verify(loginRepository).save(mockNurse);       // Proves it saved to DB
    }

    @Test
    void updateStaffRole_ThrowsExceptionIfUserIsPatient() {
        // Arrange
        Login mockPatient = new Login();
        mockPatient.setRole(Role.PATIENT);
        when(loginRepository.findById(2L)).thenReturn(Optional.of(mockPatient));

        // Act & Assert
        assertThrows(com.securehealth.backend.exception.BadRequestException.class, () -> {
            adminService.updateStaffRole(2L, "DOCTOR", "root@x.test");
        });
        verify(loginRepository, never()).save(any());
    }
}