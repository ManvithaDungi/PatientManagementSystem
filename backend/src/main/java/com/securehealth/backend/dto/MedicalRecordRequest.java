package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 2000, message = "diagnosis must be at most 2000 characters")
    private String diagnosis;

    @NotBlank(message = "Symptoms are required")
    @Size(max = 10000, message = "symptoms must be at most 10000 characters")
    private String symptoms;

    @NotBlank(message = "Treatment provided is required")
    @Size(max = 10000, message = "treatmentProvided must be at most 10000 characters")
    private String treatmentProvided;

    @Size(max = 10000, message = "notes must be at most 10000 characters")
    private String notes;

    @Size(max = 255, message = "attachmentUrl must be at most 255 characters")
    private String attachmentUrl; // filename returned by POST /api/files/upload
}