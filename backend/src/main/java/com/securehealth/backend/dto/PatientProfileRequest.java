package com.securehealth.backend.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * Data Transfer Object for requesting changes to a patient's profile.
 * <p>
 * Used when a user updates their personal information, such as name, 
 * birth date, gender, contact details, and medical history.
 * </p>
 */
@Data
public class PatientProfileRequest {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String contactNumber;
    private String address;
    private String medicalHistory;
}