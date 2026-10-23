package com.securehealth.backend.service;

import com.securehealth.backend.dto.LabTestDTO;
import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.LabTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabTechnicianServiceTest {

    @Mock
    private LabTestRepository labTestRepository;

    @InjectMocks
    private LabTechnicianService labTechnicianService;

    @Test
    void getDashboardOverview_ReturnsStatsAndRecentActivity() {
        when(labTestRepository.countByStatusIgnoreCase("Pending")).thenReturn(5L);
        when(labTestRepository.countByStatusIgnoreCase("Collected")).thenReturn(3L);
        when(labTestRepository.countByStatusIgnoreCase("Results Pending")).thenReturn(2L);
        when(labTestRepository.countByStatusIgnoreCase("Completed")).thenReturn(10L);
        
        LabTest activity = new LabTest();
        activity.setTestId(101L);
        when(labTestRepository.findTop10ByOrderByOrderedAtDesc()).thenReturn(List.of(activity));

        Map<String, Object> overview = labTechnicianService.getDashboardOverview();

        assertEquals(5L, overview.get("pending"));
        assertEquals(3L, overview.get("collected"));
        assertEquals(2L, overview.get("resultsPending"));
        assertEquals(10L, overview.get("completed"));
        assertEquals(1, ((List<?>) overview.get("recentActivity")).size());
    }

    @Test
    void getAllOrders_WithStatusFilter_ReturnsFilteredList() {
        LabTest test = new LabTest();
        test.setTestId(1L);
        when(labTestRepository.findByStatusIgnoreCaseOrderByOrderedAtAsc("Pending")).thenReturn(List.of(test));

        List<LabTestDTO> result = labTechnicianService.getAllOrders("Pending");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getTestId());
        verify(labTestRepository, times(1)).findByStatusIgnoreCaseOrderByOrderedAtAsc("Pending");
    }

    @Test
    void getAllOrders_NoStatusFilter_ReturnsAll() {
        LabTest test = new LabTest();
        test.setTestId(1L);
        when(labTestRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(test)));

        List<LabTestDTO> result = labTechnicianService.getAllOrders(null);

        assertEquals(1, result.size());
        verify(labTestRepository, times(1)).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void updateOrderStatus_ValidTest_ReturnsUpdatedDTO() {
        LabTest test = new LabTest();
        test.setTestId(1L);
        test.setStatus("Pending");
        
        when(labTestRepository.findById(1L)).thenReturn(Optional.of(test));
        when(labTestRepository.save(any(LabTest.class))).thenReturn(test);

        LabTestDTO result = labTechnicianService.updateOrderStatus(1L, "Collected");

        assertEquals("Collected", result.getStatus());
        verify(labTestRepository, times(1)).save(test);
    }
    
    @Test
    void updateOrderStatus_InvalidTest_ThrowsException() {
        when(labTestRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> labTechnicianService.updateOrderStatus(1L, "Collected"));
        verify(labTestRepository, never()).save(any());
    }

    @Test
    void uploadResults_ValidTest_ReturnsDTOWithResultsAndCompletedStatus() {
        LabTest test = new LabTest();
        test.setTestId(1L);
        test.setStatus("Results Pending");
        
        when(labTestRepository.findById(1L)).thenReturn(Optional.of(test));
        when(labTestRepository.save(any(LabTest.class))).thenReturn(test);

        LabTestDTO result = labTechnicianService.uploadResults(1L, "12.5", "Normal range", "http://example.com/file.pdf");

        assertEquals("Completed", result.getStatus());
        assertEquals("12.5", result.getResultValue());
        assertEquals("Normal range", result.getRemarks());
        assertEquals("http://example.com/file.pdf", result.getFileUrl());
        verify(labTestRepository, times(1)).save(test);
    }
}
