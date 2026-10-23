package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing a set of patient vital sign readings.
 * <p>
 * Records clinical measurements like blood pressure, heart rate, and temperature 
 * at a specific point in time, typically performed by a nurse.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "vital_signs", indexes = {
        @Index(name = "idx_vital_signs_patient_recorded", columnList = "patient_profile_id, recorded_at")
})
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vital_id")
    private Long vitalSignId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id", nullable = false)
    private Login nurse;

    private String bloodPressure;
    private Integer heartRate;
    private Double temperature;
    private Integer respiratoryRate;
    private Integer oxygenSaturation;
    private Double weight;
    private Double height;

    private Integer painLevel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 30)
    private String temperatureRoute;

    @Column(updatable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();
}