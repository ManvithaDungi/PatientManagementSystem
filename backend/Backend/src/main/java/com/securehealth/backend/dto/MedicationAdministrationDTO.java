package com.securehealth.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing a single medication administration record.
 */
@Data
public class MedicationAdministrationDTO {
    private Long id;
    private Long prescriptionId;
    private String medicationName;
    private Long patientId;
    private String nurseEmail;
    private LocalDateTime administeredAt;
    private String notes;
}
