package com.securehealth.backend.controller;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Prescription;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.PrescriptionRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import com.securehealth.backend.service.PrescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrescriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PrescriptionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PrescriptionRepository prescriptionRepository;
    @MockBean private PatientAccessValidator accessValidator;
    @MockBean private PrescriptionService prescriptionService;

    @MockBean private com.securehealth.backend.util.JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;
    @MockBean private AuditLogRepository auditLogRepository;

    private Prescription prescriptionFor(Long profileId) {
        Login patientLogin = new Login();
        patientLogin.setEmail("patient@mail.com");
        PatientProfile patient = new PatientProfile();
        patient.setProfileId(profileId);
        patient.setUser(patientLogin);

        Prescription rx = new Prescription();
        rx.setPrescriptionId(3L);
        rx.setPatient(patient);
        rx.setMedicationName("Amoxicillin");
        return rx;
    }

    @Test
    void getById_NotFound_Returns404() throws Exception {
        when(prescriptionRepository.findById(99L)).thenReturn(Optional.empty());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "doctor@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(get("/api/prescriptions/99").principal(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_AccessGranted_Returns200() throws Exception {
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescriptionFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/prescriptions/3").principal(auth))
                .andExpect(status().isOk());
    }

    @Test
    void getByPatient_RequestsPrescriptionsConsent() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.stranger@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for PRESCRIPTIONS"))
                .when(accessValidator).validateAccess(1L, auth, "PRESCRIPTIONS");

        mockMvc.perform(get("/api/prescriptions/patient/1").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_AccessDenied_Returns403() throws Exception {
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescriptionFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "stranger@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        doThrow(new RuntimeException("403 Forbidden: You cannot access another patient's records"))
                .when(accessValidator).validateAccess(1L, auth, "PRESCRIPTIONS");

        mockMvc.perform(get("/api/prescriptions/3").principal(auth))
                .andExpect(status().isForbidden());
    }
}
