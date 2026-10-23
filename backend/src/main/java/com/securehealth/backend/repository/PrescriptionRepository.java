package com.securehealth.backend.repository;

import com.securehealth.backend.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link Prescription} entities.
 * <p>
 * Facilitates retrieval of medication prescriptions for patients, 
 * including filtering by status (active vs historical) and chronological sorting.
 * </p>
 */
@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /** Everything a doctor has prescribed, with patient and doctor fetched in the same query (no N+1). */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"patient", "doctor"})
    java.util.List<Prescription> findByDoctor_UserIdOrderByIssuedAtDesc(Long doctorUserId);


    // Frontend: GET /prescriptions/patient/:patientId
    List<Prescription> findByPatient_ProfileIdOrderByIssuedAtDesc(Long patientId);
    
    // Optional but helpful: Find active prescriptions
    List<Prescription> findByPatient_ProfileIdAndStatus(Long patientId, String status);

    // Make sure java.util.List is imported
    List<Prescription> findByPatient_ProfileId(Long patientId);
}