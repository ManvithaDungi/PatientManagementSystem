package com.securehealth.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

/**
 * Data Transfer Object representing a patient's profile.
 * <p>
 * Contains personal details, contact information, date of birth, gender, 
 * address, medical history, and the ID of the assigned doctor.
 * </p>
 */
@Data
public class PatientDTO {
    private Long id; // Matches the Frontend's expectation of an 'id'

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Invalid email format")
    private String email; // From the linked Login account

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Gender is required")
    private String gender;

    @NotBlank(message = "Contact number is required")
    private String contactNumber;

    @NotBlank(message = "Address is required")
    private String address;

    private String medicalHistory;
    private Long assignedDoctorId;
}