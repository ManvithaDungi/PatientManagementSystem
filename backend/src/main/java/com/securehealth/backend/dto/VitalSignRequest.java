package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for recording new patient vital signs.
 * <p>
 * Used by medical staff to submit new vital sign readings, requiring 
 * patient identification and core metrics like blood pressure, heart rate, and temperature.
 * </p>
 */
@Data
public class VitalSignRequest {
    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotBlank(message = "Blood pressure is required")
    private String bloodPressure;     // e.g., "120/80"

    @NotNull(message = "Heart rate is required")
    private Integer heartRate;        // e.g., 75

    @NotNull(message = "Temperature is required")
    private Double temperature;       // e.g., 98.6

    private Integer respiratoryRate;  // e.g., 16
    private Integer oxygenSaturation; // e.g., 99
    private Double weight;            // in kg or lbs
    private Double height;

    @Min(value = 0, message = "painLevel must be between 0 and 10")
    @Max(value = 10, message = "painLevel must be between 0 and 10")
    private Integer painLevel;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;

    @Size(max = 30, message = "temperatureRoute must be at most 30 characters")
    private String temperatureRoute;            // in cm or inches
}