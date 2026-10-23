package com.securehealth.backend.dto;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing a prescription's details.
 * <p>
 * Transfers information about prescribed medications, including dosage, 
 * frequency, duration, status, and refill details.
 * </p>
 */
@Data
public class PrescriptionDTO {
    private Long prescriptionId;
    private Long patientId;
    private String patientName;
    private String doctorName;
    private String medicationName;
    private String dosage;
    private String frequency;
    private String duration;
    private String route;
    private String specialInstructions;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer refillsRemaining;
}