package com.securehealth.backend.dto;
import lombok.Data;
import java.time.LocalDateTime;
import com.securehealth.backend.model.AppointmentStatus;

/**
 * Data Transfer Object representing an appointment's details.
 * <p>
 * Used for transferring appointment information between the server and the client,
 * including doctor and patient names, date, status, and reason for the visit.
 * </p>
 */
@Data
public class AppointmentDTO {
    private Long appointmentId;
    private Long doctorId;
    private String doctorName;
    private Long patientId;
    private String patientName;
    private LocalDateTime appointmentDate;
    private AppointmentStatus status;
    private String reasonForVisit;
    private String appointmentType;
    private String specialRequirements;
    private String doctorNotes;
    private String cancellationReason;
}