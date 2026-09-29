package com.securehealth.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for creating a new appointment request.
 * <p>
 * This DTO is used by patients to submit appointment bookings, requiring 
 * a doctor ID, a future appointment date, and a reason for the visit.
 * </p>
 */
@Data
public class AppointmentRequest {
    @NotNull(message = "Doctor ID is required")
    private Long doctorId; // The ID of the doctor they are booking

    @NotNull(message = "Appointment date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime appointmentDate; // The exact date and time they selected

    @NotBlank(message = "Reason for visit is required")
    private String reasonForVisit; // Optional notes from the patient

    private String appointmentType; // e.g. "Consultation", "Follow-up", "Check-up", "Emergency"

    private String specialRequirements;
}