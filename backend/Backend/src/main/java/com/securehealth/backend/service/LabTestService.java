package com.securehealth.backend.service;

import com.securehealth.backend.dto.LabTestDTO;
import com.securehealth.backend.dto.LabTestRequest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.repository.LabTestRepository;
import com.securehealth.backend.repository.DoctorProfileRepository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for clinical lab test ordering and patient-facing lookups.
 * <p>
 * Allows doctors to order new tests and patients to retrieve their existing
 * test results and order history.
 * </p>
 */
@Service
public class LabTestService {

    @Autowired private LabTestRepository labTestRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private DoctorProfileRepository doctorProfileRepository;

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
     * Maps a {@link LabTest} entity to its API-safe {@link LabTestDTO}.
     * <p>
     * {@code orderedByName} and {@code orderedByDoctor} are both populated with
     * the same resolved value — earlier code populated only one or the other
     * depending on which service built the DTO, leaving the other field always
     * {@code null} to any caller that read it. Both fields are kept (rather than
     * removing one) to avoid breaking any existing frontend reader of either name.
     * </p>
     */
    private LabTestDTO mapToDTO(LabTest lt) {
        LabTestDTO dto = new LabTestDTO();
        dto.setTestId(lt.getTestId());

        if (lt.getPatient() != null) {
            dto.setPatientName((lt.getPatient().getFirstName() + " " + lt.getPatient().getLastName()).trim());
            dto.setGender(lt.getPatient().getGender());
            dto.setProfileId(lt.getPatient().getProfileId());
        }

        if (lt.getOrderedBy() != null) {
            String name = resolveOrderedByName(lt.getOrderedBy());
            dto.setOrderedByName(name);
            dto.setOrderedByDoctor(name);
            dto.setOrderedById(lt.getOrderedBy().getUserId());
        }

        dto.setTestName(lt.getTestName());
        dto.setTestCategory(lt.getTestCategory());
        dto.setResultValue(lt.getResultValue());
        dto.setUnit(lt.getUnit());
        dto.setReferenceRange(lt.getReferenceRange());
        dto.setRemarks(lt.getRemarks());
        dto.setStatus(lt.getStatus());
        dto.setFileUrl(lt.getFileUrl());
        dto.setOrderedAt(lt.getOrderedAt());
        return dto;
    }

    /**
     * Creates a new lab test order for a patient.
     *
     * @param request the {@link LabTestRequest} details
     * @param staffEmail the email of the healthcare professional ordering the test
     * @return the saved order, as a {@link LabTestDTO}
     */
    @Transactional
    public LabTestDTO createLabTest(LabTestRequest request, String staffEmail) {
        Login staff = loginRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new RuntimeException("Staff member not found"));

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("404: Patient not found"));

        LabTest labTest = new LabTest();
        labTest.setPatient(patient);
        labTest.setOrderedBy(staff);
        labTest.setStatus("PENDING");
        labTest.setTestName(request.getTestName());
        labTest.setTestCategory(request.getTestCategory());
        labTest.setRemarks(request.getRemarks());

        return mapToDTO(labTestRepository.save(labTest));
    }

    /**
     * Retrieves a single lab test by ID.
     *
     * @param id the lab test ID
     * @return the {@link LabTestDTO}, or {@code null} if not found
     */
    @Transactional(readOnly = true)
    public LabTestDTO getLabTestById(Long id) {
        return labTestRepository.findById(id).map(this::mapToDTO).orElse(null);
    }

    /**
     * Retrieves all lab tests associated with a specific patient.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link LabTestDTO} objects
     */
    @Transactional(readOnly = true)
    public List<LabTestDTO> getLabTestsByPatient(Long patientId) {
        return labTestRepository.findByPatient_ProfileId(patientId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves every lab test order in the system.
     *
     * @return a list of {@link LabTestDTO} objects
     */
    @Transactional(readOnly = true)
    public List<LabTestDTO> getAllLabTests() {
        return labTestRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all lab test orders currently in "PENDING" status.
     *
     * @return a list of {@link LabTestDTO} objects
     */
    @Transactional(readOnly = true)
    public List<LabTestDTO> getPendingLabTests() {
        return labTestRepository.findByStatusOrderByOrderedAtAsc("PENDING").stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Permanently deletes a lab test order.
     *
     * @param id the ID of the lab test to delete
     * @throws RuntimeException if the lab test is not found
     */
    @Transactional
    public void deleteLabTest(Long id) {
        if (!labTestRepository.existsById(id)) {
            throw new RuntimeException("404: Lab test not found");
        }
        labTestRepository.deleteById(id);
    }
}
