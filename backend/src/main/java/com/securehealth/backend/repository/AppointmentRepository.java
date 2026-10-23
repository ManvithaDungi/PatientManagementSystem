package com.securehealth.backend.repository;

import com.securehealth.backend.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.AppointmentStatus;

import java.util.List;
import java.time.LocalDateTime;

/**
 * Repository interface for {@link Appointment} entities.
 * <p>
 * Provides methods for retrieving appointments by patient or doctor, 
 * checking for scheduling conflicts, and calculating administrative metrics.
 * </p>
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Frontend: GET /appointments/patient/:patientId
    List<Appointment> findByPatient_ProfileIdOrderByAppointmentDateDesc(Long patientId);

    // Frontend: GET /appointments/doctor/:doctorId
    // Finds all appointments for a doctor between the start and end of a specific
    // day
    List<Appointment> findByDoctor_UserIdAndAppointmentDateBetween(
            Long doctorId,
            java.time.LocalDateTime startOfDay,
            java.time.LocalDateTime endOfDay);

    // Checks if the doctor already has a non-cancelled appointment at this exact
    // time
    boolean existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
            Long doctorId,
            java.time.LocalDateTime appointmentDate,
            java.util.List<AppointmentStatus> statuses);

    List<Appointment> findByDoctor_UserIdOrderByAppointmentDateAsc(Long doctorId);

    @Query("SELECT DISTINCT a.patient FROM Appointment a " +
           "WHERE a.doctor.userId = :doctorId " +
           "AND a.status IN ('SCHEDULED', 'COMPLETED')")
    List<PatientProfile> findDistinctPatientsByDoctorId(@Param("doctorId") Long doctorId);

    List<Appointment> findByPatient_ProfileId(Long patientId);
    List<Appointment> findByDoctor_UserId(Long doctorId);
    
    // Counts appointments by their exact status
    long countByStatus(AppointmentStatus status);

    // Admin needs to see all pending approvals
    List<Appointment> findByStatus(AppointmentStatus status);

    // Counts scheduled appointments for a specific time range (today)
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDate >= :startOfDay " +
           "AND a.appointmentDate < :endOfDay AND a.status = 'SCHEDULED'")
    long countTodaysAppointments(@Param("startOfDay") LocalDateTime startOfDay, 
                                 @Param("endOfDay") LocalDateTime endOfDay);

    boolean existsByDoctor_UserIdAndPatient_ProfileIdAndStatusIn(
            Long doctorId, Long patientProfileId, java.util.Collection<AppointmentStatus> statuses);
}
