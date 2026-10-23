package com.securehealth.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securehealth.backend.dto.AdminMetricsDTO;
import com.securehealth.backend.dto.RoleUpdateDTO;
import com.securehealth.backend.dto.StaffDTO;
import com.securehealth.backend.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 1. Notice we removed all @SpringBootTest and @WebMvcTest annotations
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        // 2. The Magic: We build a standalone mock environment just for this controller
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    // Helper method to simulate a logged-in Admin
    private UsernamePasswordAuthenticationToken getAdminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@hospital.com", null, List.of(new SimpleGrantedAuthority("ADMIN")));
    }

    // Helper method to simulate a logged-in Patient (to test 403 Forbidden)
    private UsernamePasswordAuthenticationToken getPatientAuth() {
        return new UsernamePasswordAuthenticationToken(
                "patient@mail.com", null, List.of(new SimpleGrantedAuthority("PATIENT")));
    }

    @Test
    void getDashboardMetrics_AsAdmin_Returns200() throws Exception {
        AdminMetricsDTO mockMetrics = new AdminMetricsDTO();
        mockMetrics.setTotalPatients(100);
        mockMetrics.setTotalDoctors(10);
        when(adminService.getDashboardMetrics()).thenReturn(mockMetrics);

        mockMvc.perform(get("/api/admin/metrics")
                .principal(getAdminAuth())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPatients").value(100))
                .andExpect(jsonPath("$.totalDoctors").value(10));
    }


    @Test
    void updateStaffRole_ValidRequest_Returns200() throws Exception {
        RoleUpdateDTO requestDto = new RoleUpdateDTO();
        requestDto.setNewRole("ADMIN");

        StaffDTO responseDto = new StaffDTO();
        responseDto.setUserId(1L);
        responseDto.setRole("ADMIN");

        when(adminService.updateStaffRole(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("ADMIN"), org.mockito.ArgumentMatchers.any())).thenReturn(responseDto);

        mockMvc.perform(put("/api/admin/staff/1/role")
                .principal(getAdminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}