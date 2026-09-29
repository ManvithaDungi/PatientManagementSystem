package com.securehealth.backend.service;

import com.securehealth.backend.dto.DoctorDTO;
import com.securehealth.backend.model.DoctorProfile;
import com.securehealth.backend.repository.DoctorProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing doctor profiles and professional information.
 * <p>
 * Handles retrieval of doctors by specialty or department, and allows 
 * doctors to update their own professional details and shift schedules.
 * </p>
 */
@Service
public class DoctorService {

    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    @Autowired
    private com.securehealth.backend.repository.LoginRepository loginRepository;

    /**
     * Retrieves a list of all doctor profiles in the system.
     *
     * @return a list of {@link DoctorDTO} objects
     */
    @Transactional(readOnly = true)
    public List<DoctorDTO> getAllDoctors() {
        return doctorProfileRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific doctor profile by its ID.
     *
     * @param id the ID of the doctor profile
     * @return the {@link DoctorDTO} for the specified doctor
     * @throws RuntimeException if the doctor is not found
     */
    @Transactional(readOnly = true)
    public DoctorDTO getDoctorById(Long id) {
        DoctorProfile profile = doctorProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Doctor not found"));
        return mapToDTO(profile);
    }

    /**
     * Retrieves the doctor profile belonging to the currently authenticated user.
     * <p>
     * Resolves by {@code Login.userId} (JWT identity) rather than requiring the
     * caller to know its own {@code DoctorProfile.profileId} — avoids the
     * profileId/userId ambiguity that {@link #getDoctorById(Long)} carries.
     * </p>
     *
     * @param email the email of the authenticated doctor (from the JWT)
     * @return the {@link DoctorDTO} for the calling doctor
     * @throws RuntimeException if no doctor profile exists for this user
     */
    @Transactional(readOnly = true)
    public DoctorDTO getMyProfile(String email) {
        com.securehealth.backend.model.Login user = loginRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("404: User not found"));
        DoctorProfile profile = doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("404: Doctor profile not found"));
        return mapToDTO(profile);
    }

    /**
     * Filters doctor profiles by their medical specialty.
     *
     * @param specialty the specialty to filter by (case-insensitive)
     * @return a list of matching {@link DoctorDTO} objects
     */
    @Transactional(readOnly = true)
    public List<DoctorDTO> getDoctorsBySpecialty(String specialty) {
        return doctorProfileRepository.findBySpecialtyIgnoreCase(specialty).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Filters doctor profiles by their assigned department.
     *
     * @param department the department to filter by (case-insensitive)
     * @return a list of matching {@link DoctorDTO} objects
     */
    @Transactional(readOnly = true)
    public List<DoctorDTO> getDoctorsByDepartment(String department) {
        return doctorProfileRepository.findByDepartmentIgnoreCase(department).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing doctor profile.
     * <p>
     * Enforces security by ensuring only the profile owner or an administrator 
     * can perform the update.
     * </p>
     *
     * @param id the ID of the profile to update
     * @param dto the {@link DoctorDTO} containing updated information
     * @param requesterEmail the email of the user requesting the update
     * @param requesterRole the role of the user requesting the update
     * @return the updated {@link DoctorDTO}
     * @throws RuntimeException if the doctor is not found or access is forbidden
     */
    @Transactional
    public DoctorDTO updateDoctorProfile(Long id, DoctorDTO dto, String requesterEmail, String requesterRole) {
        DoctorProfile profile = doctorProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("404: Doctor not found"));

        // Security Check: Only the owning Doctor or an Admin can edit this profile
        if (!requesterRole.equals("ADMIN") && !profile.getUser().getEmail().equals(requesterEmail)) {
            throw new RuntimeException("403 Forbidden: You can only update your own profile");
        }

        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setSpecialty(dto.getSpecialty());
        profile.setContactNumber(dto.getContactNumber());
        profile.setDepartment(dto.getDepartment());
        
        // Update Scheduling Fields
        if (dto.getShiftStartTime() != null) profile.setShiftStartTime(dto.getShiftStartTime());
        if (dto.getShiftEndTime() != null) profile.setShiftEndTime(dto.getShiftEndTime());
        if (dto.getSlotDurationMinutes() != null) profile.setSlotDurationMinutes(dto.getSlotDurationMinutes());
        if (dto.getWorkingDays() != null) profile.setWorkingDays(dto.getWorkingDays());

        return mapToDTO(doctorProfileRepository.save(profile));
    }

    /**
     * Updates the profile belonging to the currently authenticated doctor.
     * <p>
     * Resolves by {@code Login.userId} — no ownership check is needed since the
     * profile is looked up from the caller's own identity, not a path parameter.
     * </p>
     *
     * @param email the email of the authenticated doctor (from the JWT)
     * @param dto    the updated fields
     * @return the updated {@link DoctorDTO}
     */
    @Transactional
    public DoctorDTO updateMyProfile(String email, DoctorDTO dto) {
        com.securehealth.backend.model.Login user = loginRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("404: User not found"));
        DoctorProfile profile = doctorProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("404: Doctor profile not found"));

        profile.setFirstName(dto.getFirstName());
        profile.setLastName(dto.getLastName());
        profile.setSpecialty(dto.getSpecialty());
        profile.setContactNumber(dto.getContactNumber());
        profile.setDepartment(dto.getDepartment());

        if (dto.getShiftStartTime() != null) profile.setShiftStartTime(dto.getShiftStartTime());
        if (dto.getShiftEndTime() != null) profile.setShiftEndTime(dto.getShiftEndTime());
        if (dto.getSlotDurationMinutes() != null) profile.setSlotDurationMinutes(dto.getSlotDurationMinutes());
        if (dto.getWorkingDays() != null) profile.setWorkingDays(dto.getWorkingDays());

        return mapToDTO(doctorProfileRepository.save(profile));
    }

    // --- Helper Method ---
    private DoctorDTO mapToDTO(DoctorProfile profile) {
        DoctorDTO dto = new DoctorDTO();
        dto.setId(profile.getProfileId());
        dto.setUserId(profile.getUser().getUserId());
        dto.setFirstName(profile.getFirstName());
        dto.setLastName(profile.getLastName());
        dto.setEmail(profile.getUser().getEmail());
        dto.setSpecialty(profile.getSpecialty());
        dto.setContactNumber(profile.getContactNumber());
        dto.setDepartment(profile.getDepartment());
        
        // Map Scheduling Fields to DTO
        dto.setShiftStartTime(profile.getShiftStartTime());
        dto.setShiftEndTime(profile.getShiftEndTime());
        dto.setSlotDurationMinutes(profile.getSlotDurationMinutes());
        dto.setWorkingDays(profile.getWorkingDays());
        
        return dto;
    }
}