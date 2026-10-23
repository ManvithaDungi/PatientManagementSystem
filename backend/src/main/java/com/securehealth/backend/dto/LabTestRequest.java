package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for creating or updating a lab test request.
 * <p>
 * Used to specify the patient, test name, category, and initial results or remarks 
 * when ordering or recording a lab test.
 * </p>
 */
@Data
public class LabTestRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotBlank(message = "Test name is required")
    @Size(max = 255, message = "testName must be at most 255 characters")
    private String testName;         // e.g., "Complete Blood Count"

    @NotBlank(message = "Test category is required")
    @Size(max = 255, message = "testCategory must be at most 255 characters")
    private String testCategory;     // e.g., "Hematology"

    @Size(max = 255, message = "resultValue must be at most 255 characters")
    private String resultValue;      // e.g., "14.5"
    @Size(max = 100, message = "unit must be at most 100 characters")
    private String unit;             // e.g., "g/dL"
    @Size(max = 255, message = "referenceRange must be at most 255 characters")
    private String referenceRange;   // e.g., "13.8 - 17.2"
    @Size(max = 2000, message = "remarks must be at most 2000 characters")
    private String remarks;          // e.g., "Normal"
}