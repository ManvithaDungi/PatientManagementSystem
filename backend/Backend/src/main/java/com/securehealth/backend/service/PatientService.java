package com.securehealth.backend.service;

import com.securehealth.backend.dto.PatientDTO;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Role; // Assuming you have a Role enum
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing patient profiles and administrative directory lookups.
 * <p>
 * Handles profile creation, secure retrieval with IDOR protection, and 
 * updates for personal and medical history data.
 * </p>
 */
@Service
public class PatientService {

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ConsentService consentService;

    /**
     * GET /patients
     * Only Doctors and Admins should be able to pull a full list of patients.
     */
    /**
     * Retrieves a paginated list of all patients in the system.
     * <p>
     * Restricted to healthcare professionals (DOCTOR/ADMIN).
     * </p>
     *
     * @param requesterRole the role of the user making the request
     * @param pageable pagination and sorting information
     * @return a page of {@link PatientDTO} objects
     * @throws RuntimeException if the requester has insufficient privileges
     */
    @Transactional(readOnly = true)
    public Page<PatientDTO> getAllPatients(String requesterRole, Pageable pageable) {
        if (!requesterRole.equals("DOCTOR") && !requesterRole.equals("ADMIN")) {
            throw new RuntimeException("403 Forbidden: Insufficient privileges");
        }
        
        return patientProfileRepository.findAll(pageable)
                .map(this::mapToDTO);
    }

    /**
     * GET /patients/:id
     * SECURITY CORE: Prevents IDOR. 
     */
    /**
     * Retrieves a specific patient profile by its ID.
     * <p>
     * Implements strict IDOR protection: Patients can only access their own 
     * profiles, while Doctors and Admins have general access.
     * </p>
     *
     * @param id the ID of the patient profile
     * @param requesterEmail the email of the authenticated requester
     * @param requesterRole the role of the authenticated requester
     * @return the {@link PatientDTO} of the requested profile
     * @throws RuntimeException if the patient is not found or access is forbidden
     */
    @Transactional(readOnly = true)
    public PatientDTO getPatientById(Long id, String requesterEmail, String requesterRole) {
        PatientProfile profile = patientProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Patient not found"));

        // Admins have full oversight access.
        if (requesterRole.equals("ADMIN")) {
            return mapToDTO(profile);
        }

        // A doctor with a direct care relationship (assigned to this patient) needs no separate consent.
        // Any other doctor must have been granted explicit patient consent.
        if (requesterRole.equals("DOCTOR")) {
            boolean isAssignedDoctor = profile.getAssignedDoctor() != null
                    && profile.getAssignedDoctor().getEmail().equals(requesterEmail);
            if (!isAssignedDoctor) {
                Login doctor = loginRepository.findByEmail(requesterEmail)
                        .orElseThrow(() -> new RuntimeException("404: Doctor not found"));
                if (!consentService.hasConsent(profile.getProfileId(), doctor.getUserId(), "ALL")) {
                    throw new RuntimeException("403 Forbidden: No active patient consent on file");
                }
            }
            return mapToDTO(profile);
        }

        // Patient access check: They can only see the profile if it belongs to their own Login
        if (profile.getUser().getEmail().equals(requesterEmail)) {
            return mapToDTO(profile);
        }

        // If they reach here, they are trying to access someone else's profile
        throw new RuntimeException("403 Forbidden: Unauthorized access to patient records");
    }

    /**
     * GET /patients/me
     * SECURITY CORE: Gets the currently logged in patient's profile.
     */
    /**
     * Retrieves the profile associated with a specific user email.
     *
     * @param email the user's email
     * @return the {@link PatientDTO} of the profile
     * @throws RuntimeException if the user or profile is not found
     */
    @Transactional(readOnly = true)
    public PatientDTO getPatientByEmail(String email) {
        Login user = loginRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        PatientProfile profile = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("404: Patient profile not found"));
        return mapToDTO(profile);
    }

    /**
     * POST /patients
     * Creates a profile and links it to the logged-in user.
     */
    /**
     * Creates a new patient profile for an existing user account.
     *
     * @param dto the profile details
     * @param requesterEmail the email of the user to link the profile to
     * @return the saved {@link PatientDTO}
     * @throws RuntimeException if a profile already exists for the user
     */
    @Transactional
    public PatientDTO createPatientProfile(PatientDTO dto, String requesterEmail) {
        Login user = loginRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (patientProfileRepository.findByUser(user).isPresent()) {
            throw new RuntimeException("400: Profile already exists for this user");
        }

        PatientProfile profile = new PatientProfile();
        profile.setUser(user);
        updateProfileFields(profile, dto);
        
        // Optional: Link doctor if provided and requester is Admin
        if (dto.getAssignedDoctorId() != null) {
             loginRepository.findById(dto.getAssignedDoctorId())
                 .ifPresent(profile::setAssignedDoctor);
        }

        return mapToDTO(patientProfileRepository.save(profile));
    }

    /**
     * PUT /patients/:id
     */
    /**
     * Updates an existing patient profile.
     * <p>
     * Enforces security by ensuring patients only update their own records.
     * </p>
     *
     * @param id the ID of the profile to update
     * @param dto the {@link PatientDTO} with updated fields
     * @param requesterEmail the email of the requester
     * @param requesterRole the role of the requester
     * @return the updated {@link PatientDTO}
     * @throws RuntimeException if the profile is not found or access is forbidden
     */
    @Transactional
    public PatientDTO updatePatientProfile(Long id, PatientDTO dto, String requesterEmail, String requesterRole) {
        PatientProfile profile = patientProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Patient not found"));

        // Authorization check
        if (!requesterRole.equals("ADMIN") && !profile.getUser().getEmail().equals(requesterEmail)) {
            throw new RuntimeException("403 Forbidden: Cannot update another patient's profile");
        }

        updateProfileFields(profile, dto);
        return mapToDTO(patientProfileRepository.save(profile));
    }

    /**
     * Retrieves a distinct list of all patients who have an active or completed 
     * appointment with a specific doctor.
     */
    /**
     * Retrieves a list of patients who have active or history with a specific doctor.
     * <p>
     * Restricted to the doctor themselves or administrators.
     * </p>
     *
     * @param doctorId the ID of the doctor
     * @param requesterEmail the email of the requester
     * @param requesterRole the role of the requester
     * @return a list of {@link PatientDTO} objects
     * @throws RuntimeException if the doctor is not found or access is forbidden
     */
    @Transactional(readOnly = true)
    public List<PatientDTO> getPatientsByDoctor(Long doctorId, String requesterEmail, String requesterRole) {
        // Find the doctor to verify identity
        Login doctor = loginRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("404: Doctor not found"));

        // Strict RBAC: Only the doctor themselves (or an Admin) can view their patient list
        if (!requesterRole.equals("ADMIN") && !doctor.getEmail().equals(requesterEmail)) {
            throw new RuntimeException("403 Forbidden: You can only view your own patient list.");
        }

        // Fetch distinct profiles and map them to secure DTOs
        return appointmentRepository.findDistinctPatientsByDoctorId(doctorId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // --- Helper Methods ---

    private void updateProfileFields(PatientProfile profile, PatientDTO dto) {
        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setDateOfBirth(dto.getDateOfBirth());
        profile.setGender(dto.getGender());
        profile.setContactNumber(dto.getContactNumber());
        profile.setAddress(dto.getAddress());
        profile.setMedicalHistory(dto.getMedicalHistory());
    }

    private PatientDTO mapToDTO(PatientProfile profile) {
        PatientDTO dto = new PatientDTO();
        dto.setId(profile.getProfileId());
        dto.setFirstName(profile.getFirstName());
        dto.setLastName(profile.getLastName());
        dto.setEmail(profile.getUser().getEmail());
        dto.setDateOfBirth(profile.getDateOfBirth());
        dto.setGender(profile.getGender());
        dto.setContactNumber(profile.getContactNumber());
        dto.setAddress(profile.getAddress());
        dto.setMedicalHistory(profile.getMedicalHistory());
        
        if (profile.getAssignedDoctor() != null) {
            dto.setAssignedDoctorId(profile.getAssignedDoctor().getUserId());
        }
        return dto;
    }
}