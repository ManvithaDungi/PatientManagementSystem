package com.securehealth.backend.controller;

import com.securehealth.backend.dto.DoctorDTO;
import com.securehealth.backend.dto.PatientDTO;
import com.securehealth.backend.service.DoctorService;
import com.securehealth.backend.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorController.class)
@AutoConfigureMockMvc(addFilters = false)
public class DoctorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private PatientService patientService;

    // --- SECURITY DEPENDENCIES ---
    @MockBean private com.securehealth.backend.util.JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;
    @MockBean private com.securehealth.backend.repository.AuditLogRepository auditLogRepository;

    @Test
    void getPatientsByDoctor_Returns200() throws Exception {
        // Arrange
        PatientDTO mockPatient = new PatientDTO();
        mockPatient.setId(1L);
        mockPatient.setFirstName("John");
        mockPatient.setLastName("Doe");

        when(patientService.getPatientsByDoctor(anyLong(), anyString(), anyString()))
                .thenReturn(Collections.singletonList(mockPatient));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.house@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        // Act & Assert
        mockMvc.perform(get("/api/doctors/2/patients")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstName").value("John"));
    }

    @Test
    void getPatientsByDoctor_AsPatient_Returns403() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/doctors/2/patients")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateDoctorProfile_AsDoctor_Returns200() throws Exception {
        DoctorDTO updated = new DoctorDTO();
        updated.setId(2L);
        updated.setFirstName("Greg");
        when(doctorService.updateDoctorProfile(anyLong(), any(), anyString(), anyString())).thenReturn(updated);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "dr.house@mail.com", null, List.of(new SimpleGrantedAuthority("DOCTOR")));

        mockMvc.perform(put("/api/doctors/2")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Greg"));
    }

    @Test
    void updateDoctorProfile_AsNurse_Returns403() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "nurse@mail.com", null, List.of(new SimpleGrantedAuthority("NURSE")));

        mockMvc.perform(put("/api/doctors/2")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}