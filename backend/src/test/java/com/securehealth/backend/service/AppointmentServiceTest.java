package com.securehealth.backend.service;

import com.securehealth.backend.dto.AppointmentDTO;
import com.securehealth.backend.dto.AppointmentRequest;
import com.securehealth.backend.model.Appointment;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.DoctorProfileRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(MockitoExtension.class)
public class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private LoginRepository loginRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private DoctorProfileRepository doctorProfileRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    // MUST BE @InjectMocks, NOT @Mock!
    @InjectMocks private AppointmentService appointmentService;

    private Login patientLogin;
    private Login doctorLogin;
    private PatientProfile patientProfile;
    private Appointment pendingAppointment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        patientLogin = new Login();
        patientLogin.setEmail("patient@mail.com");

        doctorLogin = new Login();
        doctorLogin.setUserId(2L);
        doctorLogin.setRole(com.securehealth.backend.model.Role.DOCTOR);

        patientProfile = new PatientProfile();
        patientProfile.setUser(patientLogin);

        pendingAppointment = new Appointment();
        pendingAppointment.setAppointmentId(10L);
        pendingAppointment.setStatus(AppointmentStatus.PENDING_APPROVAL);
        pendingAppointment.setDoctor(doctorLogin);
        pendingAppointment.setPatient(patientProfile);

        // toDTO() resolves a display name via DoctorProfileRepository; no profile
        // row is set up for this test's doctor, so it falls back to the login email.
        lenient().when(doctorProfileRepository.findByUser(any())).thenReturn(Optional.empty());

        // A doctor who works every day 09:00-17:00 in 30-minute slots
        com.securehealth.backend.model.DoctorProfile schedule = new com.securehealth.backend.model.DoctorProfile();
        schedule.setShiftStartTime(java.time.LocalTime.of(9, 0));
        schedule.setShiftEndTime(java.time.LocalTime.of(17, 0));
        schedule.setSlotDurationMinutes(30);
        schedule.setWorkingDays(java.util.List.of(java.time.DayOfWeek.values()));
        lenient().when(doctorProfileRepository.findByUser_UserId(2L)).thenReturn(Optional.of(schedule));
    }

    @Test
    void createAppointment_SetsStatusToPending() {
        AppointmentRequest request = new AppointmentRequest();
        request.setDoctorId(2L);
        request.setAppointmentDate(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));

        // Use anyString() and any() to guarantee these mocks don't fail unexpectedly
        when(loginRepository.findByEmail(anyString())).thenReturn(Optional.of(patientLogin));
        when(patientProfileRepository.findByUser(any())).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findById(anyLong())).thenReturn(Optional.of(doctorLogin));
        
        when(appointmentRepository.existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
                anyLong(), any(), any())).thenReturn(false);

        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(i -> i.getArguments()[0]);

        AppointmentDTO result = appointmentService.createAppointment(request, "patient@mail.com");

        assertEquals(AppointmentStatus.PENDING_APPROVAL, result.getStatus());
        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
    }

    @Test
    void createAppointment_ConstraintViolationFromRaceIsReportedAs409() {
        AppointmentRequest request = new AppointmentRequest();
        request.setDoctorId(2L);
        request.setAppointmentDate(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));

        when(loginRepository.findByEmail(anyString())).thenReturn(Optional.of(patientLogin));
        when(patientProfileRepository.findByUser(any())).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findById(anyLong())).thenReturn(Optional.of(doctorLogin));
        // Both racing requests pass the existence check...
        when(appointmentRepository.existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
                anyLong(), any(), any())).thenReturn(false);
        // ...and the database's unique active_slot_key rejects the second insert.
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> appointmentService.createAppointment(request, "patient@mail.com"));

        assertTrue(exception.getMessage().contains("409"));
    }

    @Test
    void createAppointment_Throws409WhenSlotTaken() {
        AppointmentRequest request = new AppointmentRequest();
        request.setDoctorId(2L);
        request.setAppointmentDate(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));

        when(loginRepository.findByEmail(anyString())).thenReturn(Optional.of(patientLogin));
        when(patientProfileRepository.findByUser(any())).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findById(anyLong())).thenReturn(Optional.of(doctorLogin));
        
        // Force the mock to say the slot IS taken
        when(appointmentRepository.existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
                anyLong(), any(), any())).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> appointmentService.createAppointment(request, "patient@mail.com"));
            
        assertTrue(exception.getMessage().contains("409"));
    }

    @Test
    void approveAppointment_ChangesStatusToScheduled() {
        when(appointmentRepository.findById(anyLong())).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArguments()[0]);

        AppointmentDTO approved = appointmentService.approveAppointment(10L);

        assertEquals(AppointmentStatus.SCHEDULED, approved.getStatus());
    }

    @Test
    void approveAppointment_ThrowsErrorIfNotPending() {
        pendingAppointment.setStatus(AppointmentStatus.SCHEDULED); // Already scheduled
        when(appointmentRepository.findById(anyLong())).thenReturn(Optional.of(pendingAppointment));

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> appointmentService.approveAppointment(10L));
            
        assertTrue(exception.getMessage().contains("Only pending appointments"));
    }

    @Test
    void createAppointment_RejectsNonDoctorLogin() {
        Login notDoctor = new Login();
        notDoctor.setUserId(9L);
        notDoctor.setRole(com.securehealth.backend.model.Role.ADMIN);
        AppointmentRequest request = new AppointmentRequest();
        request.setDoctorId(9L);
        request.setAppointmentDate(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        when(loginRepository.findByEmail(anyString())).thenReturn(Optional.of(patientLogin));
        when(patientProfileRepository.findByUser(any())).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findById(9L)).thenReturn(Optional.of(notDoctor));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> appointmentService.createAppointment(request, "patient@mail.com"));
        assertTrue(ex.getMessage().contains("Doctor not found"));
    }

    @Test
    void createAppointment_RejectsPastDates() {
        AppointmentRequest request = new AppointmentRequest();
        request.setDoctorId(2L);
        request.setAppointmentDate(LocalDateTime.now().minusHours(1));
        when(loginRepository.findByEmail(anyString())).thenReturn(Optional.of(patientLogin));
        when(patientProfileRepository.findByUser(any())).thenReturn(Optional.of(patientProfile));
        when(loginRepository.findById(anyLong())).thenReturn(Optional.of(doctorLogin));

        assertThrows(RuntimeException.class,
                () -> appointmentService.createAppointment(request, "patient@mail.com"));
    }
}
