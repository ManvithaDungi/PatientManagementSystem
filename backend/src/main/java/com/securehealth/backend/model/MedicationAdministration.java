package com.securehealth.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity recording a single instance of a nurse administering a prescribed
 * medication to a patient.
 * <p>
 * Distinct from {@link Prescription}, which represents the doctor's order —
 * a prescription can be administered many times over its course (e.g. "twice
 * daily for 7 days" produces up to 14 administration records).
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "medication_administrations")
public class MedicationAdministration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "user"})
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash", "otp", "otpExpiry"})
    private Login nurse;

    @Column(nullable = false)
    private LocalDateTime administeredAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String notes;
}
