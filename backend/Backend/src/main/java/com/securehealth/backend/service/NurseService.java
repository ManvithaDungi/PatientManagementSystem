package com.securehealth.backend.service;

import com.securehealth.backend.model.HandoverNote;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.MedicationAdministration;
import com.securehealth.backend.model.NurseTask;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Prescription;
import com.securehealth.backend.dto.MedicationAdministrationDTO;
import com.securehealth.backend.repository.HandoverNoteRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.MedicationAdministrationRepository;
import com.securehealth.backend.repository.NurseTaskRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.repository.PrescriptionRepository;
import com.securehealth.backend.repository.VitalSignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for nursing operations and clinical task management.
 * <p>
 * Provides a dedicated dashboard for nurses to track assigned patients, 
 * monitor pending vitals and medications, manage shift handovers, and 
 * update nursing task statuses.
 * </p>
 */
@Service
@Transactional
public class NurseService {

    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private NurseTaskRepository nurseTaskRepository;
    @Autowired private HandoverNoteRepository handoverNoteRepository;
    @Autowired private VitalSignRepository vitalSignRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private MedicationAdministrationRepository medicationAdministrationRepository;

    private Login getAuthUser(String email) {
        return loginRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Nurse not found with email: " + email));
    }

    /**
     * Aggregates clinical dashboard metrics for a specific nurse.
     * <p>
     * Includes counts of assigned patients, pending/overdue tasks, 
     * and a countdown to the next scheduled medication.
     * </p>
     *
     * @param nurseEmail the email of the nurse
     * @return a map containing various clinical activity metrics
     */
    public Map<String, Object> getDashboardOverview(String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);
        Long nurseId = nurse.getUserId();

        // 1. Assigned Patients
        long assignedPatientsCount = patientProfileRepository.findByAssignedNurse(nurse).size();

        // 2. Pending Tasks
        long pendingTasks = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalse(nurseId);
        long overdueTasks = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndDueTimeBefore(nurseId, LocalDateTime.now());
        long highPriorityTasks = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndPriority(nurseId, "high");
        // 3. Vitals (Tracked as NurseTasks with category "vitals")
        long pendingVitals = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCase(nurseId, "vitals");
        long overdueVitals = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseAndDueTimeBefore(nurseId, "vitals", LocalDateTime.now());

        // 4. Medications (Tracked as NurseTasks with category "medication")
        long medicationsDue = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCase(nurseId, "medication");
        long overdueMedications = nurseTaskRepository.countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseAndDueTimeBefore(nurseId, "medication", LocalDateTime.now());
        
        Optional<NurseTask> nextMed = nurseTaskRepository.findFirstByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseOrderByDueTimeAsc(nurseId, "medication");
        long nextMedicationIn = -1; // -1 indicates no pending medications
        if (nextMed.isPresent() && nextMed.get().getDueTime().isAfter(LocalDateTime.now())) {
            nextMedicationIn = java.time.Duration.between(LocalDateTime.now(), nextMed.get().getDueTime()).toMinutes();
        } else if (nextMed.isPresent() && nextMed.get().getDueTime().isBefore(LocalDateTime.now())) {
            nextMedicationIn = 0; // It's overdue/due right now
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("assignedPatients", assignedPatientsCount);
        stats.put("pendingVitals", pendingVitals);
        stats.put("overdueVitals", overdueVitals);
        stats.put("medicationsDue", medicationsDue);
        stats.put("overdueMedications", overdueMedications);
        stats.put("nextMedicationIn", nextMedicationIn);
        stats.put("pendingTasks", pendingTasks);
        stats.put("overdueTasks", overdueTasks);
        stats.put("highPriorityTasks", highPriorityTasks);

        return stats;
    }

    /**
     * Retrieves a list of patients assigned to a specific nurse.
     *
     * @param nurseEmail the email of the nurse
     * @return a list of {@link PatientProfile} entities
     */
    public List<PatientProfile> getAssignedPatients(String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);
        return patientProfileRepository.findByAssignedNurse(nurse);
    }

    /**
     * Retrieves all nursing tasks assigned to a specific nurse, ordered by due time.
     *
     * @param nurseEmail the email of the nurse
     * @return a list of {@link NurseTask} entities
     */
    public List<NurseTask> getTasks(String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);
        return nurseTaskRepository.findByAssignedNurse_UserIdOrderByDueTimeAsc(nurse.getUserId());
    }

    /**
     * Toggles the completion status of a nursing task.
     *
     * @param taskId the ID of the task to toggle
     * @param nurseEmail the email of the nurse performing the action (for authorization)
     * @return a result map containing a success message and the updated task
     * @throws RuntimeException if the task is not found or not assigned to the requester
     */
    public NurseTask createTask(Map<String, Object> payload, String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);

        if (payload.get("title") == null || payload.get("title").toString().isBlank()) {
            throw new RuntimeException("Task title is required.");
        }
        if (payload.get("dueTime") == null || payload.get("dueTime").toString().isBlank()) {
            throw new RuntimeException("Due time is required.");
        }

        NurseTask task = new NurseTask();
        task.setAssignedNurse(nurse);
        task.setTitle(payload.get("title").toString().trim());
        task.setCategory(payload.getOrDefault("category", "general").toString());
        task.setPriority(payload.getOrDefault("priority", "medium").toString());
        task.setCompleted(false);
        task.setStatus("upcoming");

        if (payload.containsKey("description") && payload.get("description") != null) {
            task.setDescription(payload.get("description").toString());
        }

        task.setDueTime(LocalDateTime.parse(payload.get("dueTime").toString()));

        if (payload.containsKey("patientId") && payload.get("patientId") != null && !payload.get("patientId").toString().isBlank()) {
            Long patientId = Long.valueOf(payload.get("patientId").toString());
            patientProfileRepository.findById(patientId).ifPresent(task::setPatient);
        }

        return nurseTaskRepository.save(task);
    }

    public Map<String, Object> toggleTaskStatus(Long taskId, String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);
        NurseTask task = nurseTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!task.getAssignedNurse().getUserId().equals(nurse.getUserId())) {
            throw new RuntimeException("Not authorized to modify this task.");
        }

        task.setCompleted(!task.isCompleted());
        if (task.isCompleted()) {
            task.setPreviousStatus(task.getStatus());
            task.setStatus("completed");
        } else {
            task.setStatus(task.getPreviousStatus() != null ? task.getPreviousStatus() : "upcoming");
        }

        nurseTaskRepository.save(task);
        return Map.of("message", "Task toggled successfully.", "task", task);
    }

    /**
     * Retrieves shift handover notes, separated by direction.
     *
     * @param nurseEmail the email of the nurse (currently unused for filtering)
     * @return a map containing notes "fromPreviousShift" and "forNextShift"
     */
    public Map<String, Object> getHandoverNotes(String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);
        
        List<HandoverNote> fromPrevious = handoverNoteRepository.findByShiftDirectionOrderByTimestampDesc("FROM_PREVIOUS");
        List<HandoverNote> forNext = handoverNoteRepository.findByShiftDirectionOrderByTimestampDesc("FOR_NEXT");

        return Map.of(
                "fromPreviousShift", fromPrevious,
                "forNextShift", forNext
        );
    }

    /**
     * Saves a new shift handover note.
     *
     * @param payload a map containing 'content', 'type', 'priority', 'direction', and optional 'patientId'
     * @param nurseEmail the email of the nurse authoring the note
     * @return the saved {@link HandoverNote} entity
     */
    public HandoverNote saveHandoverNote(Map<String, Object> payload, String nurseEmail) {
        Login nurse = getAuthUser(nurseEmail);
        
        HandoverNote note = new HandoverNote();
        note.setAuthor(nurse);
        note.setContent(payload.getOrDefault("content", "").toString());
        note.setType(payload.getOrDefault("type", "general").toString());
        note.setPriority(payload.getOrDefault("priority", "normal").toString());
        note.setShiftDirection(payload.getOrDefault("direction", "FOR_NEXT").toString());

        // Assuming patient ID is optionally passed
        if (payload.containsKey("patientId")) {
            Long patientId = Long.valueOf(payload.get("patientId").toString());
            PatientProfile patient = patientProfileRepository.findById(patientId).orElse(null);
            note.setPatient(patient);
        }

        return handoverNoteRepository.save(note);
    }

    /**
     * Records that a nurse administered a dose of a prescribed medication.
     * <p>
     * Creates one {@link MedicationAdministration} row per dose given — a single
     * prescription (e.g. "twice daily for 7 days") accumulates many of these
     * over its course. This is intentionally separate from the {@link Prescription}
     * itself, which represents the doctor's order, not administration history.
     * </p>
     *
     * @param prescriptionId the prescription being administered
     * @param nurseEmail     the email of the administering nurse
     * @param notes          optional clinical notes about this administration
     * @return the saved administration record, as a {@link MedicationAdministrationDTO}
     * @throws RuntimeException if the prescription does not exist
     */
    public MedicationAdministrationDTO recordMedicationAdministration(Long prescriptionId, String nurseEmail, String notes) {
        Login nurse = getAuthUser(nurseEmail);
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new RuntimeException("404: Prescription not found"));

        MedicationAdministration record = new MedicationAdministration();
        record.setPrescription(prescription);
        record.setPatient(prescription.getPatient());
        record.setNurse(nurse);
        record.setAdministeredAt(LocalDateTime.now());
        record.setNotes(notes);

        MedicationAdministration saved = medicationAdministrationRepository.save(record);
        return mapAdministrationToDTO(saved);
    }

    /**
     * Retrieves the medication administration history for a single prescription,
     * most recent first.
     *
     * @param prescriptionId the prescription to look up
     * @return a list of {@link MedicationAdministrationDTO} objects
     */
    public List<MedicationAdministrationDTO> getAdministrationHistory(Long prescriptionId) {
        return medicationAdministrationRepository
                .findByPrescription_PrescriptionIdOrderByAdministeredAtDesc(prescriptionId)
                .stream()
                .map(this::mapAdministrationToDTO)
                .collect(Collectors.toList());
    }

    private MedicationAdministrationDTO mapAdministrationToDTO(MedicationAdministration record) {
        MedicationAdministrationDTO dto = new MedicationAdministrationDTO();
        dto.setId(record.getId());
        dto.setPrescriptionId(record.getPrescription().getPrescriptionId());
        dto.setMedicationName(record.getPrescription().getMedicationName());
        dto.setPatientId(record.getPatient() != null ? record.getPatient().getProfileId() : null);
        dto.setNurseEmail(record.getNurse() != null ? record.getNurse().getEmail() : null);
        dto.setAdministeredAt(record.getAdministeredAt());
        dto.setNotes(record.getNotes());
        return dto;
    }
}
