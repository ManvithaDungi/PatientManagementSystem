package com.securehealth.backend.dto;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing a medical record's details.
 * <p>
 * Transports information about a patient's diagnosis, symptoms, treatments, 
 * and notes, along with the recording doctor's name and relevant timestamps.
 * </p>
 */
@Data
public class MedicalRecordDTO {
    private Long recordId;
    private Long patientId;
    private String doctorName;
    private String diagnosis;
    private String symptoms;
    private String treatmentProvided;
    private String notes;
    private String attachmentUrl;
    private LocalDateTime recordDate;
    private LocalDateTime createdAt;
}