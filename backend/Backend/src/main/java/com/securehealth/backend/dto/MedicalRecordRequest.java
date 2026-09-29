package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Data Transfer Object for creating or updating a medical record request.
 * <p>
 * Used by medical professionals to submit new medical records, requiring 
 * the patient ID, diagnosis, symptoms, and treatment details.
 * </p>
 */
@Data
public class MedicalRecordRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotBlank(message = "Diagnosis is required")
    private String diagnosis;

    @NotBlank(message = "Symptoms are required")
    private String symptoms;

    @NotBlank(message = "Treatment provided is required")
    private String treatmentProvided;

    private String notes;
}