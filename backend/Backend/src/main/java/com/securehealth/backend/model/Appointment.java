package com.securehealth.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing a medical appointment.
 * <p>
 * Links a patient profile with a doctor (Login) and tracks the date, 
 * status, reason for visit, and any doctor notes for the encounter.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long appointmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "user"})
    private PatientProfile patient;

    // Doctor associated with the appointment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash", "otp", "otpExpiry"})
    private Login doctor;

    @Column(nullable = false)
    private LocalDateTime appointmentDate;

    // Status: PENDING_APPROVAL, SCHEDULED, COMPLETED, CANCELLED, NO_SHOW, REJECTED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING_APPROVAL;

    @Column(columnDefinition = "TEXT")
    private String reasonForVisit;

    // e.g. "Consultation", "Follow-up", "Check-up", "Emergency" — free text, not enum-constrained
    private String appointmentType;

    @Column(columnDefinition = "TEXT")
    private String specialRequirements;

    @Column(columnDefinition = "TEXT")
    private String doctorNotes;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}