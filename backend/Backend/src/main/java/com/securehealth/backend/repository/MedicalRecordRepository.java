package com.securehealth.backend.repository;

import com.securehealth.backend.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for {@link MedicalRecord} entities.
 * <p>
 * Provides methods for retrieving clinical encounter records for patients, 
 * typically ordered by creation date to show a chronological history.
 * </p>
 */
@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    // Frontend: GET /medical-records/patient/:patientId
    List<MedicalRecord> findByPatient_ProfileIdOrderByCreatedAtDesc(Long patientId);

    // Make sure java.util.List is imported
    List<MedicalRecord> findByPatient_ProfileId(Long patientId);

    // Used to resolve a stored filename back to its owning patient for file-download authorization
    Optional<MedicalRecord> findByAttachmentUrl(String attachmentUrl);
}