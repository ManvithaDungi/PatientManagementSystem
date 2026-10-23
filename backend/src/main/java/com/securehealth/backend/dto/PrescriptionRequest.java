package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Size(max = 255, message = "medicationName must be at most 255 characters")
    private String medicationName;

    @NotBlank(message = "Dosage is required")
    @Size(max = 255, message = "dosage must be at most 255 characters")
    private String dosage;

    @NotBlank(message = "Frequency is required")
    @Size(max = 255, message = "frequency must be at most 255 characters")
    private String frequency;

    @NotBlank(message = "Duration is required")
    @Size(max = 255, message = "duration must be at most 255 characters")
    private String duration;

    @Size(max = 5000, message = "specialInstructions must be at most 5000 characters")
    private String specialInstructions;

    @Size(max = 100, message = "route must be at most 100 characters")
    private String route;

    @Min(value = 0, message = "refillsAllowed cannot be negative")
    @Max(value = 12, message = "refillsAllowed cannot exceed 12")
    private Integer refillsAllowed; // refills granted when the prescription is created // e.g. "Oral", "IV", "IM", "Topical" — defaults to "Oral" if omitted
}