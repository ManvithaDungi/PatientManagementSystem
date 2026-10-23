package com.securehealth.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointments_doctor_date", columnList = "doctor_id, appointment_date"),
        @Index(name = "idx_appointments_patient_date", columnList = "patient_profile_id, appointment_date"),
        @Index(name = "idx_appointments_status", columnList = "status")
})
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

    /**
     * Database-level double-booking guard.
     * <p>
     * Holds {@code "<doctorUserId>|<appointmentDate>"} while the appointment
     * occupies its slot, and {@code NULL} once it is CANCELLED or REJECTED.
     * The column is UNIQUE, and unique indexes treat NULLs as distinct, so two
     * live bookings for the same doctor and time cannot coexist even when two
     * requests race past the application-level availability check.
     * </p>
     */
    @JsonIgnore
    @Column(name = "active_slot_key", unique = true)
    private String activeSlotKey;

    @PrePersist
    @PreUpdate
    void syncActiveSlotKey() {
        boolean occupiesSlot = status != AppointmentStatus.CANCELLED
                && status != AppointmentStatus.REJECTED
                && doctor != null
                && doctor.getUserId() != null
                && appointmentDate != null;
        activeSlotKey = occupiesSlot ? doctor.getUserId() + "|" + appointmentDate : null;
    }
}