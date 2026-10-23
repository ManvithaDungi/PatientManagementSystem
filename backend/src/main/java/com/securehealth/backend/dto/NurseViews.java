package com.securehealth.backend.dto;

import com.securehealth.backend.model.HandoverNote;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.NurseTask;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.VitalSign;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response views for the nurse, handover and vital-sign endpoints. These replace returning JPA entities,
 * so credential fields of the associated {@code Login} rows can never reach a client.
 */
public final class NurseViews {

    private NurseViews() {
    }

    /** Minimal public reference to a staff account. */
    @Data
    public static class UserRef {
        private Long userId;
        private String email;
        private String role;
    }

    @Data
    public static class PatientRef {
        private Long profileId;
        private String firstName;
        private String lastName;
    }

    @Data
    public static class PatientView {
        private Long profileId;
        private String firstName;
        private String lastName;
        private LocalDate dateOfBirth;
        private String gender;
        private String contactNumber;
        private String address;
        private String medicalHistory;
        private UserRef assignedDoctor;
        private UserRef assignedNurse;
    }

    @Data
    public static class TaskView {
        private Long id;
        private String title;
        private String description;
        private String category;
        private String priority;
        private String status;
        private boolean completed;
        private LocalDateTime dueTime;
        private PatientRef patient;
    }

    @Data
    public static class NoteView {
        private Long id;
        private UserRef author;
        private PatientRef patient;
        private String type;
        private String priority;
        private String content;
        private boolean read;
        private LocalDateTime timestamp;
        private String shiftDirection;
    }

    public static UserRef user(Login l) {
        if (l == null) return null;
        UserRef r = new UserRef();
        r.setUserId(l.getUserId());
        r.setEmail(l.getEmail());
        r.setRole(l.getRole() == null ? null : l.getRole().name());
        return r;
    }

    public static PatientRef patientRef(PatientProfile p) {
        if (p == null) return null;
        PatientRef r = new PatientRef();
        r.setProfileId(p.getProfileId());
        r.setFirstName(p.getFirstName());
        r.setLastName(p.getLastName());
        return r;
    }

    public static PatientView patient(PatientProfile p) {
        PatientView v = new PatientView();
        v.setProfileId(p.getProfileId());
        v.setFirstName(p.getFirstName());
        v.setLastName(p.getLastName());
        v.setDateOfBirth(p.getDateOfBirth());
        v.setGender(p.getGender());
        v.setContactNumber(p.getContactNumber());
        v.setAddress(p.getAddress());
        v.setMedicalHistory(p.getMedicalHistory());
        v.setAssignedDoctor(user(p.getAssignedDoctor()));
        v.setAssignedNurse(user(p.getAssignedNurse()));
        return v;
    }

    public static List<PatientView> patients(List<PatientProfile> list) {
        return list.stream().map(NurseViews::patient).collect(Collectors.toList());
    }

    public static TaskView task(NurseTask t) {
        TaskView v = new TaskView();
        v.setId(t.getId());
        v.setTitle(t.getTitle());
        v.setDescription(t.getDescription());
        v.setCategory(t.getCategory());
        v.setPriority(t.getPriority());
        v.setStatus(t.getStatus());
        v.setCompleted(t.isCompleted());
        v.setDueTime(t.getDueTime());
        v.setPatient(patientRef(t.getPatient()));
        return v;
    }

    public static List<TaskView> tasks(List<NurseTask> list) {
        return list.stream().map(NurseViews::task).collect(Collectors.toList());
    }

    public static NoteView note(HandoverNote n) {
        NoteView v = new NoteView();
        v.setId(n.getId());
        v.setAuthor(user(n.getAuthor()));
        v.setPatient(patientRef(n.getPatient()));
        v.setType(n.getType());
        v.setPriority(n.getPriority());
        v.setContent(n.getContent());
        v.setRead(n.isRead());
        v.setTimestamp(n.getTimestamp());
        v.setShiftDirection(n.getShiftDirection());
        return v;
    }

    public static List<NoteView> notes(List<HandoverNote> list) {
        return list.stream().map(NurseViews::note).collect(Collectors.toList());
    }

    public static VitalSignDTO vital(VitalSign v) {
        VitalSignDTO dto = new VitalSignDTO();
        dto.setVitalSignId(v.getVitalSignId());
        dto.setPatientProfileId(v.getPatient() == null ? null : v.getPatient().getProfileId());
        dto.setNurseEmail(v.getNurse() == null ? null : v.getNurse().getEmail());
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
