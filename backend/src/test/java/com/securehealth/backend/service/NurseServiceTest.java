package com.securehealth.backend.service;

import com.securehealth.backend.model.HandoverNote;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.NurseTask;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.HandoverNoteRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.NurseTaskRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseServiceTest {

    @Mock private LoginRepository loginRepository;
    @Mock private PatientProfileRepository patientProfileRepository;
    @Mock private NurseTaskRepository nurseTaskRepository;
    @Mock private HandoverNoteRepository handoverNoteRepository;

    @InjectMocks
    private NurseService nurseService;

    private Login mockNurse;
    private final String nurseEmail = "nurse@hospital.com";

    @BeforeEach
    void setUp() {
        mockNurse = new Login();
        mockNurse.setUserId(1L);
        mockNurse.setEmail(nurseEmail);
        mockNurse.setRole(Role.NURSE);
    }

    @Test
    void getDashboardOverview_ReturnsCorrectStats() {
        when(loginRepository.findByEmail(nurseEmail)).thenReturn(Optional.of(mockNurse));
        when(patientProfileRepository.findByAssignedNurse(mockNurse)).thenReturn(List.of(new PatientProfile(), new PatientProfile()));
        
        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalse(1L)).thenReturn(5L);
        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndDueTimeBefore(eq(1L), any())).thenReturn(2L);
        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndPriority(1L, "high")).thenReturn(1L);
        
        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCase(1L, "vitals")).thenReturn(3L);
        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseAndDueTimeBefore(eq(1L), eq("vitals"), any())).thenReturn(1L);

        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCase(1L, "medication")).thenReturn(4L);
        when(nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseAndDueTimeBefore(eq(1L), eq("medication"), any())).thenReturn(2L);
        
        NurseTask nextMed = new NurseTask();
        nextMed.setDueTime(LocalDateTime.now().plusMinutes(30));
        when(nurseTaskRepository.findFirstByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseOrderByDueTimeAsc(1L, "medication"))
                .thenReturn(Optional.of(nextMed));

        Map<String, Object> stats = nurseService.getDashboardOverview(nurseEmail);

        assertEquals(2L, stats.get("assignedPatients"));
        assertEquals(5L, stats.get("pendingTasks"));
        assertEquals(2L, stats.get("overdueTasks"));
        assertEquals(1L, stats.get("highPriorityTasks"));
        assertEquals(3L, stats.get("pendingVitals"));
        assertEquals(1L, stats.get("overdueVitals"));
        assertEquals(4L, stats.get("medicationsDue"));
        assertEquals(2L, stats.get("overdueMedications"));
        assertTrue((long) stats.get("nextMedicationIn") > 0);
    }

    @Test
    void getAssignedPatients_ReturnsPatients() {
        when(loginRepository.findByEmail(nurseEmail)).thenReturn(Optional.of(mockNurse));
        PatientProfile p1 = new PatientProfile();
        p1.setProfileId(101L);
        when(patientProfileRepository.findByAssignedNurse(mockNurse)).thenReturn(List.of(p1));

        List<PatientProfile> patients = nurseService.getAssignedPatients(nurseEmail);
        assertEquals(1, patients.size());
        assertEquals(101L, patients.get(0).getProfileId());
    }

    @Test
    void toggleTaskStatus_AuthorizedNurse_TogglesSuccessfully() {
        when(loginRepository.findByEmail(nurseEmail)).thenReturn(Optional.of(mockNurse));
        
        NurseTask task = new NurseTask();
        task.setId(1L);
        task.setAssignedNurse(mockNurse);
        task.setCompleted(false);
        task.setStatus("pending");
        
        when(nurseTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        Map<String, Object> result = nurseService.toggleTaskStatus(1L, nurseEmail);

        assertEquals("Task toggled successfully.", result.get("message"));
        assertTrue(((NurseTask) result.get("task")).isCompleted());
        assertEquals("completed", ((NurseTask) result.get("task")).getStatus());
        assertEquals("pending", ((NurseTask) result.get("task")).getPreviousStatus());
        verify(nurseTaskRepository, times(1)).save(task);
    }
    
    @Test
    void toggleTaskStatus_UnauthorizedNurse_ThrowsException() {
        when(loginRepository.findByEmail(nurseEmail)).thenReturn(Optional.of(mockNurse));
        
        Login otherNurse = new Login();
        otherNurse.setUserId(2L);
        
        NurseTask task = new NurseTask();
        task.setAssignedNurse(otherNurse);
        
        when(nurseTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(RuntimeException.class, () -> nurseService.toggleTaskStatus(1L, nurseEmail));
        verify(nurseTaskRepository, never()).save(any());
    }

    @Test
    void saveHandoverNote_ValidPayload_SavesSuccessfully() {
        when(loginRepository.findByEmail(nurseEmail)).thenReturn(Optional.of(mockNurse));
        
        Map<String, Object> payload = Map.of(
            "content", "Patient stable",
            "type", "Shift Change",
            "priority", "high",
            "direction", "FOR_NEXT"
        );
        
        HandoverNote savedNote = new HandoverNote();
        savedNote.setContent("Patient stable");
        when(handoverNoteRepository.save(any(HandoverNote.class))).thenReturn(savedNote);

        HandoverNote result = nurseService.saveHandoverNote(payload, nurseEmail);
        
        assertEquals("Patient stable", result.getContent());
        verify(handoverNoteRepository, times(1)).save(any(HandoverNote.class));
    }
}
