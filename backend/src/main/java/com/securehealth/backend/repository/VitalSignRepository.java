package com.securehealth.backend.repository;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for {@link VitalSign} entities.
 * <p>
 * Supports clinical tracking of physical measurements, providing chronological 
 * timelines for patients and audit trails for recording nursing staff.
 * </p>
 */
@Repository
public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {

    // Fetches a chronological timeline of a patient's vitals (essential for charts/graphs)
    List<VitalSign> findByPatientOrderByRecordedAtDesc(PatientProfile patient);

    // Audit trail: "Show me all vitals recorded by this specific nurse"
    List<VitalSign> findByNurseOrderByRecordedAtDesc(Login nurse);

    // Frontend: GET /vital-signs/patient/:patientId
    List<VitalSign> findByPatient_ProfileIdOrderByRecordedAtDesc(Long patientId);

    // Frontend: GET /vital-signs/patient/:patientId/latest
    // Spring Data JPA understands "findFirst" or "findTop"
    Optional<VitalSign> findFirstByPatient_ProfileIdOrderByRecordedAtDesc(Long patientId);
}