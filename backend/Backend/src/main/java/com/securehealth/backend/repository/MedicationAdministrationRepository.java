package com.securehealth.backend.repository;

import com.securehealth.backend.model.MedicationAdministration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link MedicationAdministration} records.
 */
@Repository
public interface MedicationAdministrationRepository extends JpaRepository<MedicationAdministration, Long> {

    List<MedicationAdministration> findByPrescription_PrescriptionIdOrderByAdministeredAtDesc(Long prescriptionId);

    List<MedicationAdministration> findByPatient_ProfileIdOrderByAdministeredAtDesc(Long patientProfileId);
}
