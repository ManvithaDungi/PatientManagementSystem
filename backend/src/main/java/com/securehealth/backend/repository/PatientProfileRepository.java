package com.securehealth.backend.repository;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for {@link PatientProfile} entities.
 * <p>
 * Supports lookups for patient profiles by their core user account 
 * and handles retrieval of assigned patient lists for individual doctors and nurses.
 * </p>
 */
@Repository
public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {

    // For a PATIENT logging in: "Get my own profile using my Login ID"
    Optional<PatientProfile> findByUser(Login user);

    // For a DOCTOR logging in: "Get a list of all patients assigned to me"
    List<PatientProfile> findByAssignedDoctor(Login doctor);
    
    // For a NURSE logging in: "Get a list of all patients assigned to me"
    List<PatientProfile> findByAssignedNurse(Login nurse);
    
    // For verifying before an update: "Does this patient belong to this doctor?"
    Optional<PatientProfile> findByProfileIdAndAssignedDoctor(Long profileId, Login doctor);
}