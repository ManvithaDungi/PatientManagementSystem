package com.securehealth.backend.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * Data Transfer Object for patient directory listings.
 * <p>
 * Provides a subset of patient details suitable for directory views, 
 * including profile and user IDs, name, email, contact info, and birth date.
 * </p>
 */
@Data
public class PatientDirectoryDTO {
    private Long profileId;
    private Long userId;       // In case the Admin needs to trigger a password reset
    private String firstName;
    private String lastName;
    private String email;      // Pulled dynamically from the Login entity
    private String contactNumber;
    private LocalDate dateOfBirth;
    private Long assignedDoctorId;  // login user id of the treating doctor, if any
    private Long assignedNurseId;   // login user id of the assigned nurse, if any
}