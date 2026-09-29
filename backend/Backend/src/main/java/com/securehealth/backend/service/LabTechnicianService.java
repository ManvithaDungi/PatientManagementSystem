package com.securehealth.backend.service;

import com.securehealth.backend.dto.LabTestDTO;
import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.repository.LabTestRepository;
import com.securehealth.backend.repository.DoctorProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for laboratory technician operations and workflow management.
 * <p>
 * Handles the laboratory test lifecycle, including dashboard metrics, 
 * order list retrieval, status transitions, and final result/report uploads.
 * </p>
 */
@Service
@Transactional
public class LabTechnicianService {

    @Autowired
    private LabTestRepository labTestRepository;

    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    /** Resolves a display name for the ordering doctor/staff member; falls back to email. */
    private String resolveOrderedByName(Login orderedBy) {
        if (orderedBy == null) {
            return "Unknown Staff";
        }
        return doctorProfileRepository.findByUser(orderedBy)
                .map(p -> (p.getFirstName() + " " + p.getLastName()).trim())
                .filter(name -> !name.isEmpty())
                .orElse(orderedBy.getEmail());
    }

    /**
     * Aggregates statistical overview data for the lab technician dashboard.
     *
     * @return a map containing status counts and recent test activity
     */
    public Map<String, Object> getDashboardOverview() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", labTestRepository.countByStatusIgnoreCase("Pending"));
        stats.put("collected", labTestRepository.countByStatusIgnoreCase("Collected"));
        stats.put("resultsPending", labTestRepository.countByStatusIgnoreCase("Results Pending"));
        stats.put("completed", labTestRepository.countByStatusIgnoreCase("Completed"));
        
        // Include recent activity feed data
        List<LabTest> recentActivity = labTestRepository.findTop10ByOrderByOrderedAtDesc();
        stats.put("recentActivity", recentActivity.stream().map(this::mapToDTO).collect(Collectors.toList()));
        return stats;
    }

    /**
     * Retrieves a list of lab test orders, optionally filtered by status.
     *
     * @param status the optional status to filter by
     * @return a list of {@link LabTestDTO} objects
     */
    public List<LabTestDTO> getAllOrders(String status) {
        List<LabTest> labTests;
        if (status != null && !status.isEmpty()) {
            labTests = labTestRepository.findByStatusOrderByOrderedAtAsc(status);
        } else {
            labTests = labTestRepository.findAll();
        }
        return labTests.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Updates the status of an existing lab test order.
     *
     * @param testId the ID of the lab test
     * @param newStatus the new status to apply
     * @return the updated {@link LabTestDTO}
     * @throws RuntimeException if the lab test is not found
     */
    public LabTestDTO updateOrderStatus(Long testId, String newStatus) {
        LabTest labTest = labTestRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Lab test not found with id: " + testId));
                
        labTest.setStatus(newStatus);
        
        // Optional: If you want to track when it was completed, you could add a completedAt field here.
        
        labTest = labTestRepository.save(labTest);
        return mapToDTO(labTest);
    }
    
    /**
     * Uploads the clinical results and optional report file for a lab test.
     * <p>
     * Automatically transitions the test status to "Completed" upon upload.
     * </p>
     *
     * @param testId the ID of the lab test
     * @param resultValue the clinical value recorded
     * @param remarks optional professional remarks or interpretations
     * @param fileUrl optional URL/path to the uploaded report file
     * @return the updated and completed {@link LabTestDTO}
     * @throws RuntimeException if the lab test is not found
     */
    public LabTestDTO uploadResults(Long testId, String resultValue, String remarks, String fileUrl) {
         LabTest labTest = labTestRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Lab test not found with id: " + testId));
        
        labTest.setResultValue(resultValue);
        
        if (remarks != null && !remarks.isEmpty()) {
            labTest.setRemarks(remarks);
        }
        
        if (fileUrl != null && !fileUrl.isEmpty()) {
            labTest.setFileUrl(fileUrl);
        }
        
        labTest.setStatus("Completed");
        
        labTest = labTestRepository.save(labTest);
        return mapToDTO(labTest);
    }

    private LabTestDTO mapToDTO(LabTest test) {
        LabTestDTO dto = new LabTestDTO();
        dto.setTestId(test.getTestId());
        
        if(test.getPatient() != null) {
            dto.setPatientName(test.getPatient().getFirstName() + " " + test.getPatient().getLastName());
            dto.setGender(test.getPatient().getGender());
            dto.setProfileId(test.getPatient().getProfileId());
        }
        
        if(test.getOrderedBy() != null) {
            String name = resolveOrderedByName(test.getOrderedBy());
            dto.setOrderedByName(name);
            dto.setOrderedByDoctor(name);
            dto.setOrderedById(test.getOrderedBy().getUserId());
        }
        
        dto.setTestName(test.getTestName());
        dto.setTestCategory(test.getTestCategory());
        dto.setResultValue(test.getResultValue());
        dto.setUnit(test.getUnit());
        dto.setReferenceRange(test.getReferenceRange());
        dto.setRemarks(test.getRemarks());
        dto.setStatus(test.getStatus());
        dto.setFileUrl(test.getFileUrl());
        dto.setOrderedAt(test.getOrderedAt());
        
        return dto;
    }
}
