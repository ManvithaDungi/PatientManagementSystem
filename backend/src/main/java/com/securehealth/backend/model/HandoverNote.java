package com.securehealth.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a handover note between nursing shifts.
 * <p>
 * Handover notes can be general or patient-specific and include a priority level,
 * the note content, and a read status to ensure critical information is communicated.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "handover_notes", indexes = {
        @Index(name = "idx_handover_direction_ts", columnList = "shift_direction, timestamp")
})
public class HandoverNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", referencedColumnName = "userId", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash"})
    private Login author;

    // Optional: Note might be tied to a specific patient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", referencedColumnName = "profileId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "assignedDoctor", "assignedNurse", "user"})
    private PatientProfile patient;

    // e.g., 'general', 'patient-specific'
    @Column(nullable = false)
    private String type = "general";

    @Column(nullable = false)
    private String priority = "normal";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // Optional: Indicates if it is for the next shift or from the previous
    private String shiftDirection;
}
