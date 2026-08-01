package com.securehealth.backend.controller;

import com.securehealth.backend.dto.AppointmentDTO;
import com.securehealth.backend.model.AppointmentStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import com.securehealth.backend.model.Appointment;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.securehealth.backend.util.JwtUtil;
import com.securehealth.backend.service.AppointmentService;
import com.securehealth.backend.service.TokenBlacklistService;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AppointmentController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
public class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- CONTROLLER DEPENDENCIES ---
    @MockBean
    private AppointmentRepository appointmentRepository;

    @MockBean
    private PatientAccessValidator accessValidator;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private LoginRepository loginRepository;

    // --- SECURITY DEPENDENCIES (Needed to satisfy the ApplicationContext) ---
    @MockBean
    private com.securehealth.backend.util.JwtUtil jwtUtil;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @MockBean
    private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;

    // FIX: AuditLogRepository must be mocked so the RequestLoggingFilter
    // (which Spring wires even in WebMvcTest) can be satisfied during context load.
    @MockBean
    private AuditLogRepository auditLogRepository;

    @Test
    @WithMockUser(username = "patient@mail.com", authorities = {"PATIENT"})
    void getAppointmentsByPatient_ReturnsList() throws Exception {
        //Arrange
        com.securehealth.backend.dto.AppointmentDTO mockDto = new com.securehealth.backend.dto.AppointmentDTO();
        mockDto.setAppointmentId(10L);
        mockDto.setDoctorName("dr.house@mail.com");
        mockDto.setPatientName("John Doe");
        mockDto.setStatus(AppointmentStatus.SCHEDULED);
        mockDto.setReasonForVisit("Routine Checkup");

        when(appointmentService.getAppointmentsByPatient(1L))
                .thenReturn(List.of(mockDto));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));
                
        mockMvc.perform(get("/api/appointments/patient/1")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appointmentId").value(10))           // Matches the DTO
                .andExpect(jsonPath("$[0].doctorName").value("dr.house@mail.com")) // Matches the DTO
                .andExpect(jsonPath("$[0].status").value(AppointmentStatus.SCHEDULED.toString()));        // Matches the DTO
    }
    @Test
    void approveAppointment_AsAdmin_Returns200() throws Exception {
        // Arrange
        AppointmentDTO approved = new AppointmentDTO();
        approved.setStatus(AppointmentStatus.SCHEDULED);
        when(appointmentService.approveAppointment(10L)).thenReturn(approved);

        // Manually create the Authentication object
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin@mail.com", null, List.of(new SimpleGrantedAuthority("ADMIN")));

        // Act & Assert
        mockMvc.perform(put("/api/appointments/10/approve")
                .principal(auth) // <-- THIS INJECTS THE AUTHENTICATION
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(AppointmentStatus.SCHEDULED.toString()));
    }

    @Test
    void approveAppointment_AsPatient_Returns403() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(put("/api/appointments/10/approve")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden: Only administrative staff can approve appointments."));
        
        verify(appointmentService, never()).approveAppointment(anyLong());
    }

    @Test
    void rejectAppointment_AsAdmin_Returns200() throws Exception {
        AppointmentDTO rejected = new AppointmentDTO();
        rejected.setStatus(AppointmentStatus.REJECTED);
        when(appointmentService.rejectAppointment(eq(10L), any())).thenReturn(rejected);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin@mail.com", null, List.of(new SimpleGrantedAuthority("ADMIN")));

        mockMvc.perform(put("/api/appointments/10/reject")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("Not in network"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(AppointmentStatus.REJECTED.toString()));
    }

    private Appointment appointmentFor(Long profileId) {
        Login patientLogin = new Login();
        patientLogin.setEmail("patient@mail.com");
        PatientProfile patient = new PatientProfile();
        patient.setProfileId(profileId);
        patient.setUser(patientLogin);

        Appointment appt = new Appointment();
        appt.setAppointmentId(10L);
        appt.setPatient(patient);
        appt.setStatus(AppointmentStatus.SCHEDULED);
        return appt;
    }

    @Test
    void getById_NotFound_Returns404() throws Exception {
        when(appointmentRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "doctor@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(get("/api/appointments/99").principal(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_AsOwningPatient_Returns200() throws Exception {
        when(appointmentRepository.findById(10L)).thenReturn(java.util.Optional.of(appointmentFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/appointments/10").principal(auth))
                .andExpect(status().isOk());
    }

    @Test
    void getById_AsOtherPatient_Returns403() throws Exception {
        when(appointmentRepository.findById(10L)).thenReturn(java.util.Optional.of(appointmentFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "stranger@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        doThrow(new RuntimeException("403 Forbidden: You cannot access another patient's records"))
                .when(accessValidator).validateAccess(1L, auth);

        mockMvc.perform(get("/api/appointments/10").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByDoctor_AsOwningDoctor_Returns200() throws Exception {
        Login doctorLogin = new Login();
        doctorLogin.setUserId(2L);
        doctorLogin.setEmail("dr.house@mail.com");
        when(loginRepository.findById(2L)).thenReturn(java.util.Optional.of(doctorLogin));
        when(appointmentService.getAppointmentsByDoctor(2L)).thenReturn(List.of());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.house@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(get("/api/appointments/doctor/2").principal(auth))
                .andExpect(status().isOk());
    }

    @Test
    void getByDoctor_AsDifferentDoctor_Returns403() throws Exception {
        Login doctorLogin = new Login();
        doctorLogin.setUserId(2L);
        doctorLogin.setEmail("dr.house@mail.com");
        when(loginRepository.findById(2L)).thenReturn(java.util.Optional.of(doctorLogin));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.strange@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(get("/api/appointments/doctor/2").principal(auth))
                .andExpect(status().isForbidden());

        verify(appointmentService, never()).getAppointmentsByDoctor(anyLong());
    }

    @Test
    void getByDoctor_AsPatient_Returns403() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/appointments/doctor/2").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_AsPatient_Returns403() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/appointments/stats").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_AsAdmin_Returns200() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin@mail.com", null, List.of(new SimpleGrantedAuthority("ADMIN")));

        mockMvc.perform(get("/api/appointments/stats").principal(auth))
                .andExpect(status().isOk());
    }
}
