package com.securehealth.backend.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;



/**
 * Entity representing a laboratory test order and result.
 * <p>
 * Tracks the patient, the ordering doctor, test details (name, category),
 * result values, units, reference ranges, and the current status of the test.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "lab_tests", indexes = {
        @Index(name = "idx_lab_tests_patient", columnList = "patient_profile_id"),
        @Index(name = "idx_lab_tests_status_ordered", columnList = "status, ordered_at")
})
public class LabTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long testId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_profile_id", nullable = false)
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_by_id", nullable = false)
    private Login orderedBy;

    private String testName;
    private String testCategory;
    private String resultValue;
    private String unit;
    private String referenceRange;
    @Column(length = 2000)
    private String remarks;
    private String status;

    private String fileUrl;

    @Column(updatable = false)
    private LocalDateTime orderedAt = LocalDateTime.now();
}