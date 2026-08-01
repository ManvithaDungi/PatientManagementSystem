package com.securehealth.backend.controller;

import com.securehealth.backend.dto.PatientDTO;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PatientControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PatientService patientService;

    @MockBean private com.securehealth.backend.util.JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;
    @MockBean private AuditLogRepository auditLogRepository;

    private static final String VALID_PATIENT_JSON = "{"
            + "\"firstName\":\"John\","
            + "\"lastName\":\"Doe\","
            + "\"email\":\"john@mail.com\","
            + "\"dateOfBirth\":\"1990-01-01\","
            + "\"gender\":\"M\","
            + "\"contactNumber\":\"1234567890\","
            + "\"address\":\"123 Main St\""
            + "}";

    private UsernamePasswordAuthenticationToken authAs(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void getAllPatients_AsDoctor_Returns200() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(Collections.emptyList());
        when(patientService.getAllPatients(anyString(), any())).thenReturn(page);

        mockMvc.perform(get("/api/patients").principal(authAs("dr.house@mail.com", "DOCTOR")))
                .andExpect(status().isOk());
    }

    @Test
    void getAllPatients_AsNurse_Returns403() throws Exception {
        mockMvc.perform(get("/api/patients").principal(authAs("nurse@mail.com", "NURSE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyProfile_AsPatient_Returns200() throws Exception {
        when(patientService.getPatientByEmail(anyString())).thenReturn(new PatientDTO());

        mockMvc.perform(get("/api/patients/me").principal(authAs("patient@mail.com", "PATIENT")))
                .andExpect(status().isOk());
    }

    @Test
    void getMyProfile_AsDoctor_Returns403() throws Exception {
        mockMvc.perform(get("/api/patients/me").principal(authAs("dr.house@mail.com", "DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPatient_AsPatient_Returns200() throws Exception {
        when(patientService.createPatientProfile(any(), anyString())).thenReturn(new PatientDTO());

        mockMvc.perform(post("/api/patients")
                        .principal(authAs("patient@mail.com", "PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PATIENT_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void createPatient_AsDoctor_Returns403() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .principal(authAs("dr.house@mail.com", "DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PATIENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePatient_AsAdmin_Returns200() throws Exception {
        when(patientService.updatePatientProfile(any(), any(), anyString(), anyString())).thenReturn(new PatientDTO());

        mockMvc.perform(put("/api/patients/1")
                        .principal(authAs("admin@mail.com", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PATIENT_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void updatePatient_AsLabTechnician_Returns403() throws Exception {
        mockMvc.perform(put("/api/patients/1")
                        .principal(authAs("tech@mail.com", "LAB_TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PATIENT_JSON))
                .andExpect(status().isForbidden());
    }
}
