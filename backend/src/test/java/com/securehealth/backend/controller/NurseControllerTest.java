package com.securehealth.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securehealth.backend.model.NurseTask;
import com.securehealth.backend.service.NurseService;
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
class NurseControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NurseService nurseService;

    @InjectMocks
    private NurseController nurseController;

    private final String nurseEmail = "nurse@hospital.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(nurseController).build();
    }

    private UsernamePasswordAuthenticationToken getNurseAuth() {
        return new UsernamePasswordAuthenticationToken(
                nurseEmail, null, List.of(new SimpleGrantedAuthority("NURSE")));
    }

    @Test
    void getDashboardOverview_Returns200() throws Exception {
        Map<String, Object> mockStats = Map.of(
                "assignedPatients", 5L,
                "pendingTasks", 10L
        );
        when(nurseService.getDashboardOverview(nurseEmail)).thenReturn(mockStats);

        mockMvc.perform(get("/api/nurse/dashboard")
                .principal(getNurseAuth())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedPatients").value(5))
                .andExpect(jsonPath("$.pendingTasks").value(10));
    }

    @Test
    void toggleTaskStatus_Returns200() throws Exception {
        NurseTask task = new NurseTask();
        task.setCompleted(true);
        task.setStatus("completed");
        
        Map<String, Object> mockResponse = Map.of(
                "message", "Task toggled successfully.",
                "task", task
        );
        
        when(nurseService.toggleTaskStatus(1L, nurseEmail)).thenReturn(mockResponse);

        mockMvc.perform(put("/api/nurse/tasks/1/toggle")
                .principal(getNurseAuth())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Task toggled successfully."));
    }
}
