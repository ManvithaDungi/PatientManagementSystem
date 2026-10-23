package com.securehealth.backend.service;

import com.securehealth.backend.dto.VitalSignRequest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.VitalSign;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.repository.VitalSignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for clinical tracking of patient vital signs.
 * <p>
 * Handles the recording of vitals by nursing staff and provides 
 * chronological timelines/latest snapshots for clinical review.
 * </p>
 */
/**
 * Service for clinical tracking of patient vital signs.
 * <p>
 * Handles the recording of vitals by nursing staff and provides 
 * chronological timelines/latest snapshots for clinical review.
 * </p>
 */
@Service
public class VitalSignService {

    @Autowired private VitalSignRepository vitalSignRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private com.securehealth.backend.security.AccessPolicy accessPolicy;

    /**
     * Records a new set of vital signs for a patient.
     *
     * @param request the {@link VitalSignRequest} details
     * @param recorderEmail the email of the person recording the vitals
     * @return the saved {@link VitalSign} entity
     */
    /**
     * Records a new set of vital signs for a patient.
     *
     * @param request the {@link VitalSignRequest} details
     * @param recorderEmail the email of the person recording the vitals
     * @return the saved {@link VitalSign} entity
     */
    @Transactional
    public VitalSign createVitalSign(VitalSignRequest request, String recorderEmail) {
        Login recorder = loginRepository.findByEmail(recorderEmail)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Staff member not found"));

        PatientProfile patient = patientProfileRepository.findById(request.getPatientId())
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Patient not found"));

        accessPolicy.checkWrite(patient.getProfileId(), recorder.getRole().name(), recorderEmail, "VITAL_SIGNS");

        VitalSign vitalSign = new VitalSign();
        vitalSign.setPatient(patient);
        vitalSign.setNurse(recorder);
        
        vitalSign.setBloodPressure(request.getBloodPressure());
        vitalSign.setHeartRate(request.getHeartRate());
        vitalSign.setTemperature(request.getTemperature());
        vitalSign.setRespiratoryRate(request.getRespiratoryRate());
        vitalSign.setOxygenSaturation(request.getOxygenSaturation());
        vitalSign.setWeight(request.getWeight());
        vitalSign.setHeight(request.getHeight());
        vitalSign.setPainLevel(request.getPainLevel());
        vitalSign.setNotes(request.getNotes());
        vitalSign.setTemperatureRoute(request.getTemperatureRoute());

        return vitalSignRepository.save(vitalSign);
    }

    /**
     * Retrieves a chronological history of vital signs for a specific patient.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link com.securehealth.backend.dto.VitalSignDTO} objects
     */
    /**
     * Retrieves a chronological history of vital signs for a specific patient.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link com.securehealth.backend.dto.VitalSignDTO} objects
     */
    @Transactional(readOnly = true)
    public java.util.List<com.securehealth.backend.dto.VitalSignDTO> getVitalSignsByPatient(Long patientId) {
        return vitalSignRepository.findByPatient_ProfileIdOrderByRecordedAtDesc(patientId)
                .stream().map(this::mapToDTO).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Retrieves the most recent vital sign recording for a specific patient.
     *
     * @param patientId the ID of the patient
     * @return the latest {@link com.securehealth.backend.dto.VitalSignDTO}
     * @throws RuntimeException if no vital signs are found for the patient
     */
    /**
     * Retrieves the most recent vital sign recording for a specific patient.
     *
     * @param patientId the ID of the patient
     * @return the latest {@link com.securehealth.backend.dto.VitalSignDTO}
     * @throws RuntimeException if no vital signs are found for the patient
     */
    @Transactional(readOnly = true)
    public com.securehealth.backend.dto.VitalSignDTO getLatestVitalSignByPatient(Long patientId) {
        return vitalSignRepository.findFirstByPatient_ProfileIdOrderByRecordedAtDesc(patientId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: No vital signs found for patient"));
    }

    /**
     * Permanently deletes a vital sign recording.
     *
     * @param id the ID of the recording to delete
     * @throws RuntimeException if the recording is not found
     */
    /**
     * Permanently deletes a vital sign recording.
     *
     * @param id the ID of the recording to delete
     * @throws RuntimeException if the recording is not found
     */
    @Transactional
    public void deleteVitalSign(Long id) {
        if (!vitalSignRepository.existsById(id)) {
            throw new com.securehealth.backend.exception.NotFoundException("404: Vital sign not found");
        }
        vitalSignRepository.deleteById(id);
    }

    private com.securehealth.backend.dto.VitalSignDTO mapToDTO(VitalSign v) {
        com.securehealth.backend.dto.VitalSignDTO dto = new com.securehealth.backend.dto.VitalSignDTO();
        dto.setVitalSignId(v.getVitalSignId());
        dto.setPatientProfileId(v.getPatient().getProfileId());
        dto.setNurseEmail(v.getNurse().getEmail());
        dto.setBloodPressure(v.getBloodPressure());
        dto.setHeartRate(v.getHeartRate());
        dto.setTemperature(v.getTemperature());
        dto.setRespiratoryRate(v.getRespiratoryRate());
        dto.setOxygenSaturation(v.getOxygenSaturation());
        dto.setWeight(v.getWeight());
        dto.setHeight(v.getHeight());
        dto.setPainLevel(v.getPainLevel());
        dto.setNotes(v.getNotes());
        dto.setTemperatureRoute(v.getTemperatureRoute());
        dto.setRecordedAt(v.getRecordedAt());
        return dto;
    }
}