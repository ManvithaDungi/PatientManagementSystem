package com.securehealth.backend.dto;

import lombok.Data;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Data Transfer Object representing a doctor's profile information.
 * <p>
 * Includes basic details like name and contact information, as well as 
 * professional details like specialty, department, and working schedule.
 * </p>
 */
@Data
public class DoctorDTO {
    /** DoctorProfile.profileId — identifies the profile row itself. */
    private Long id;
    /** Login.userId — the identity/booking ID used by AppointmentController, PatientController, etc. */
    private Long userId;
    private String firstName;
    private String lastName;
    private String email; 
    private String specialty;
    private String contactNumber;
    private String department;
    private LocalTime shiftStartTime;
    private LocalTime shiftEndTime;
    private Integer slotDurationMinutes;
    private List<DayOfWeek> workingDays;
}