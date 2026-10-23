package com.securehealth.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a patient's detailed profile.
 * <p>
 * Stores personal information, contact details, medical history, and 
 * handles relationships with assigned doctors and nurses for coordinated care.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "patient_profiles")
public class PatientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long profileId;

    // 1-to-1 relationship: This profile belongs to exactly one Login account
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Login user;

    // Many-to-1: Many patients can be assigned to one Doctor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_doctor_id", referencedColumnName = "userId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Login assignedDoctor;

    // Many-to-1: Many patients can be assigned to one Nurse
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_nurse_id", referencedColumnName = "userId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Login assignedNurse;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    private String gender;

    private String contactNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String medicalHistory; // High-level notes (allergies, chronic conditions)

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}