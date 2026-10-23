package com.securehealth.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securehealth.backend.dto.LabTestDTO;
import com.securehealth.backend.service.LabTechnicianService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LabTechnicianControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LabTechnicianService labTechnicianService;

    @InjectMocks
    private LabTechnicianController labTechnicianController;

    private final String techEmail = "tech@hospital.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(labTechnicianController).build();
    }

    private UsernamePasswordAuthenticationToken getTechAuth() {
        return new UsernamePasswordAuthenticationToken(
                techEmail, null, List.of(new SimpleGrantedAuthority("LAB_TECHNICIAN")));
    }

    @Test
    void getDashboardOverview_Returns200() throws Exception {
        Map<String, Object> mockStats = Map.of(
                "pending", 5L,
                "completed", 10L
        );
        when(labTechnicianService.getDashboardOverview()).thenReturn(mockStats);

        mockMvc.perform(get("/api/lab-technician/dashboard")
                .principal(getTechAuth())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(5))
                .andExpect(jsonPath("$.completed").value(10));
    }

    @Test
    void updateOrderStatus_Returns200() throws Exception {
        LabTestDTO mockResponse = new LabTestDTO();
        mockResponse.setTestId(1L);
        mockResponse.setStatus("Collected");

        when(labTechnicianService.updateOrderStatus(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("Collected"), org.mockito.ArgumentMatchers.any())).thenReturn(mockResponse);

        mockMvc.perform(put("/api/lab-technician/orders/1/status")
                .principal(getTechAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"Collected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Collected"))
                .andExpect(jsonPath("$.testId").value(1));
    }

    @Test
    void uploadResults_Returns200() throws Exception {
        LabTestDTO mockResponse = new LabTestDTO();
        mockResponse.setTestId(1L);
        mockResponse.setStatus("Completed");
        mockResponse.setResultValue("Normal");

        when(labTechnicianService.uploadResults(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("Normal"), org.mockito.ArgumentMatchers.eq("OK"), org.mockito.ArgumentMatchers.eq("url"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(mockResponse);

        mockMvc.perform(put("/api/lab-technician/orders/1/upload")
                .principal(getTechAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultValue\": \"Normal\", \"remarks\": \"OK\", \"fileUrl\": \"url\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.resultValue").value("Normal"));
    }
}
