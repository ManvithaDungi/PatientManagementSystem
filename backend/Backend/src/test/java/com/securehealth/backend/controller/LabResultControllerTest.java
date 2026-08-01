package com.securehealth.backend.controller;

import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.LabTestRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import com.securehealth.backend.service.LabTestService;
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

@WebMvcTest(LabResultController.class)
@AutoConfigureMockMvc(addFilters = false)
public class LabResultControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LabTestRepository labTestRepository;
    @MockBean private PatientAccessValidator accessValidator;
    @MockBean private LabTestService labTestService;

    @MockBean private com.securehealth.backend.util.JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;
    @MockBean private AuditLogRepository auditLogRepository;

    private LabTest labTestFor(Long profileId) {
        Login patientLogin = new Login();
        patientLogin.setEmail("patient@mail.com");
        PatientProfile patient = new PatientProfile();
        patient.setProfileId(profileId);
        patient.setUser(patientLogin);

        LabTest test = new LabTest();
        test.setTestId(8L);
        test.setPatient(patient);
        test.setTestName("CBC");
        return test;
    }

    @Test
    void getById_NotFound_Returns404() throws Exception {
        when(labTestRepository.findById(99L)).thenReturn(Optional.empty());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "doctor@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(get("/api/lab-results/99").principal(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_AccessGranted_Returns200() throws Exception {
        when(labTestRepository.findById(8L)).thenReturn(Optional.of(labTestFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/lab-results/8").principal(auth))
                .andExpect(status().isOk());
    }

    @Test
    void getByPatient_RequestsLabResultsConsent() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.stranger@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for LAB_RESULTS"))
                .when(accessValidator).validateAccess(1L, auth, "LAB_RESULTS");

        mockMvc.perform(get("/api/lab-results/patient/1").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_AccessDenied_Returns403() throws Exception {
        when(labTestRepository.findById(8L)).thenReturn(Optional.of(labTestFor(1L)));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "stranger@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        doThrow(new RuntimeException("403 Forbidden: You cannot access another patient's records"))
                .when(accessValidator).validateAccess(1L, auth, "LAB_RESULTS");

        mockMvc.perform(get("/api/lab-results/8").principal(auth))
                .andExpect(status().isForbidden());
    }
}
