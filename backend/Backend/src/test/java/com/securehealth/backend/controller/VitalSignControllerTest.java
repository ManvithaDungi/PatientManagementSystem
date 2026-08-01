package com.securehealth.backend.controller;

import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.VitalSignRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import com.securehealth.backend.service.VitalSignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VitalSignController.class)
@AutoConfigureMockMvc(addFilters = false)
public class VitalSignControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private VitalSignRepository vitalSignRepository;
    @MockBean private PatientAccessValidator accessValidator;
    @MockBean private VitalSignService vitalSignService;

    @MockBean private com.securehealth.backend.util.JwtUtil jwtUtil;
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @MockBean private com.securehealth.backend.service.TokenBlacklistService tokenBlacklistService;
    @MockBean private AuditLogRepository auditLogRepository;

    @Test
    void getByPatient_AsOwningPatient_Returns200() throws Exception {
        when(vitalSignService.getVitalSignsByPatient(1L)).thenReturn(List.of());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));

        mockMvc.perform(get("/api/vital-signs/patient/1").principal(auth))
                .andExpect(status().isOk());
    }

    @Test
    void getByPatient_RequestsVitalSignsConsent() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "nurse.stranger@mail.com", null, List.of(new SimpleGrantedAuthority("NURSE")));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for VITAL_SIGNS"))
                .when(accessValidator).validateAccess(1L, auth, "VITAL_SIGNS");

        mockMvc.perform(get("/api/vital-signs/patient/1").principal(auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLatestByPatient_RequestsVitalSignsConsent() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "nurse.stranger@mail.com", null, List.of(new SimpleGrantedAuthority("NURSE")));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for VITAL_SIGNS"))
                .when(accessValidator).validateAccess(1L, auth, "VITAL_SIGNS");

        mockMvc.perform(get("/api/vital-signs/patient/1/latest").principal(auth))
                .andExpect(status().isForbidden());
    }
}
