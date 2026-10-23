package com.securehealth.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a clinical task assigned to a nurse.
 * <p>
 * Tasks are categorized (e.g., medication, assessment), prioritized, 
 * and tracked for completion against a due time, often associated with a specific patient.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nurse_tasks", indexes = {
        @Index(name = "idx_nurse_tasks_nurse_completed_due", columnList = "assigned_nurse_id, completed, due_time")
})
public class NurseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The nurse assigned to this task
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_nurse_id", referencedColumnName = "userId", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash"})
    private Login assignedNurse;

    // Optional: Task might be tied to a specific patient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", referencedColumnName = "profileId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "assignedDoctor", "assignedNurse", "user"})
    private PatientProfile patient;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // e.g., 'medication', 'assessment', 'care', 'documentation'
    @Column(nullable = false)
    private String category;

    // e.g., 'critical', 'high', 'medium', 'low'
    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private LocalDateTime dueTime;

    private boolean completed = false;

    // E.g., 'upcoming', 'due-soon', 'overdue', 'completed'
    private String status = "upcoming";

    private String previousStatus;
}
