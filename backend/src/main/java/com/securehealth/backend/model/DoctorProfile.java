package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Entity representing a doctor's professional profile.
 * <p>
 * Stores specialized information for doctors, such as their specialty, 
 * department, and shift schedule, while linking back to their core 
 * authentication account.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "doctor_profiles")
public class DoctorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long profileId;

    // 1-to-1 with Login (Auth)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private Login user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    // This fulfills the frontend's GET /doctors/specialty/:specialty
    @Column(nullable = false)
    private String specialty; 

    private String contactNumber;
    
    private String department;
    
    // e.g., 09:00 (9 AM)
    @Column(nullable = false)
    private LocalTime shiftStartTime = LocalTime.of(9, 0); 

    // e.g., 17:00 (5 PM)
    @Column(nullable = false)
    private LocalTime shiftEndTime = LocalTime.of(17, 0); 

    // e.g., 30 minutes per appointment
    @Column(nullable = false)
    private Integer slotDurationMinutes = 30; 

    // e.g., [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
    @ElementCollection(targetClass = DayOfWeek.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "doctor_working_days", joinColumns = @JoinColumn(name = "doctor_profile_id"))
    @Enumerated(EnumType.STRING)
    private List<DayOfWeek> workingDays;
}