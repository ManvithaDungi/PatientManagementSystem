package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Data Transfer Object for creating or updating a prescription request.
 * <p>
 * Used by doctors to prescribe medications, specifying the patient, 
 * medication name, dosage, frequency, duration, and any special instructions.
 * </p>
 */
@Data
public class PrescriptionRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotBlank(message = "Medication name is required")
    private String medicationName;

    @NotBlank(message = "Dosage is required")
    private String dosage;

    @NotBlank(message = "Frequency is required")
    private String frequency;

    @NotBlank(message = "Duration is required")
    private String duration;

    private String specialInstructions;

    private String route; // e.g. "Oral", "IV", "IM", "Topical" — defaults to "Oral" if omitted
}