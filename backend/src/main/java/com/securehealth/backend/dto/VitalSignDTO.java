package com.securehealth.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing patient vital sign readings.
 * <p>
 * Contains recorded values for blood pressure, heart rate, temperature, 
 * respiratory rate, oxygen saturation, weight, and height, along with 
 * recording metadata.
 * </p>
 */
@Data
public class VitalSignDTO {
    private Long vitalSignId;
    private Long patientProfileId;
    private String nurseEmail; // To show who recorded it
    
    private String bloodPressure;
    private Integer heartRate;
    private Double temperature;
    private Integer respiratoryRate;
    private Integer oxygenSaturation;
    private Double weight;
    private Double height;
    private Integer painLevel;
    private String notes;
    private String temperatureRoute;
    
    private LocalDateTime recordedAt;
}