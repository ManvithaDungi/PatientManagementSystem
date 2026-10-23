package com.securehealth.backend.service;

import com.securehealth.backend.dto.AppointmentRequest;
import com.securehealth.backend.model.Appointment;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.model.DoctorProfile;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.repository.AppointmentRepository;
import com.securehealth.backend.repository.DoctorProfileRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.dto.AppointmentDTO;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

/**
 * Service for managing medical appointments and scheduling.
 * <p>
 * Handles available slot calculation based on doctor shifts, creation of
 * appointment requests, administrative approval/rejection, and status updates.
 * </p>
 */
@Service
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NotificationService notificationService;

    /** Resolves a display name for a doctor; falls back to email if no profile exists. */
    private String resolveDoctorName(Login doctor) {
        if (doctor == null) {
            return "Unknown";
        }
        return doctorProfileRepository.findByUser(doctor)
                .map(p -> (p.getFirstName() + " " + p.getLastName()).trim())
                .filter(name -> !name.isEmpty())
                .orElse(doctor.getEmail());
    }

    /**
     * Maps an {@link Appointment} entity to its API-safe {@link AppointmentDTO}.
     * <p>
     * Every read/write path in this service returns through this single mapper —
     * never the raw entity — since {@code Appointment.doctor} carries a {@code Login}
     * object with no JSON guard on {@code passwordHash}/{@code otp}.
     * </p>
     */
    private AppointmentDTO toDTO(Appointment app) {
        AppointmentDTO dto = new AppointmentDTO();
        dto.setAppointmentId(app.getAppointmentId());
        dto.setDoctorId(app.getDoctor() != null ? app.getDoctor().getUserId() : null);
        dto.setDoctorName(resolveDoctorName(app.getDoctor()));
        dto.setPatientId(app.getPatient() != null ? app.getPatient().getProfileId() : null);
        dto.setPatientName(app.getPatient() != null
                ? (app.getPatient().getFirstName() + " " + app.getPatient().getLastName()).trim()
                : "Unknown");
        dto.setAppointmentDate(app.getAppointmentDate());
        dto.setStatus(app.getStatus());
        dto.setReasonForVisit(app.getReasonForVisit());
        dto.setAppointmentType(app.getAppointmentType());
        dto.setSpecialRequirements(app.getSpecialRequirements());
        dto.setDoctorNotes(app.getDoctorNotes());
        dto.setCancellationReason(app.getCancellationReason());
        return dto;
    }

    /**
     * Calculates available time slots for a doctor on a specific date.
     *
     * @param doctorId the ID of the doctor
     * @param targetDate the date to check availability for
     * @return a list of {@link LocalTime} representing available slots
     */
    private static final List<AppointmentStatus> FREE_STATUSES =
            List.of(AppointmentStatus.CANCELLED, AppointmentStatus.REJECTED);

    private void requireTransition(Appointment appointment, AppointmentStatus next) {
        if (!appointment.getStatus().canTransitionTo(next)) {
            throw new com.securehealth.backend.exception.BadRequestException("400: A " + appointment.getStatus().name().toLowerCase().replace('_', ' ')
                    + " appointment cannot become " + next.name().toLowerCase().replace('_', ' ') + ".");
        }
    }

    private DoctorProfile scheduleOf(Long doctorUserId) {
        DoctorProfile profile = doctorProfileRepository.findByUser_UserId(doctorUserId)
                .orElseThrow(() -> new com.securehealth.backend.exception.BadRequestException("400: This doctor has no schedule and cannot be booked."));
        if (profile.getShiftStartTime() == null || profile.getShiftEndTime() == null
                || profile.getSlotDurationMinutes() == null || profile.getSlotDurationMinutes() <= 0) {
            throw new com.securehealth.backend.exception.BadRequestException("400: This doctor's schedule is not configured.");
        }
        return profile;
    }

    /** The requested time must be in the future, on a working day, inside the shift and on the slot grid. */
    private void requireBookable(Long doctorUserId, LocalDateTime when) {
        if (when == null || !when.isAfter(LocalDateTime.now())) {
            throw new com.securehealth.backend.exception.BadRequestException("400: Appointment must be in the future.");
        }
        DoctorProfile profile = scheduleOf(doctorUserId);
        if (when.getSecond() != 0 || when.getNano() != 0) {
            throw new com.securehealth.backend.exception.BadRequestException("400: Appointment time must be on a whole minute.");
        }
        if (profile.getWorkingDays() == null || !profile.getWorkingDays().contains(when.getDayOfWeek())) {
            throw new com.securehealth.backend.exception.BadRequestException("400: The doctor does not work on that day.");
        }
        LocalTime time = when.toLocalTime();
        LocalTime start = profile.getShiftStartTime();
        long slot = profile.getSlotDurationMinutes();
        boolean insideShift = !time.isBefore(start) && !time.plusMinutes(slot).isAfter(profile.getShiftEndTime());
        long offset = java.time.Duration.between(start, time).toMinutes();
        if (!insideShift || offset % slot != 0) {
            throw new com.securehealth.backend.exception.BadRequestException("400: That time is not an available appointment slot.");
        }
    }

    public List<LocalTime> getAvailableSlots(Long doctorId, LocalDate targetDate) {
        // 1. Fetch Doctor Profile (Note: doctorId is the Login ID, so we find by User)
        DoctorProfile doctor = doctorProfileRepository.findByUser_UserId(doctorId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("Doctor not found"));
        scheduleOf(doctorId);

        // 2. Is the doctor even working on this day of the week?
        if (doctor.getWorkingDays() == null || !doctor.getWorkingDays().contains(targetDate.getDayOfWeek())) {
            return new ArrayList<>(); // Returns empty list (No slots available)
        }

        // 3. Fetch ALREADY BOOKED appointments for this exact date
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);
        List<Appointment> bookedAppointments = appointmentRepository
                .findByDoctor_UserIdAndAppointmentDateBetween(doctorId, startOfDay, endOfDay);

        // Extract just the times of the booked appointments
        List<LocalTime> bookedTimes = bookedAppointments.stream()
                .filter(apt -> !FREE_STATUSES.contains(apt.getStatus())) // cancelled and rejected slots are free again
                .map(apt -> apt.getAppointmentDate().toLocalTime())
                .toList();

        // 4. Generate ALL possible slots for the day based on shift hours and slot
        // duration
        List<LocalTime> availableSlots = new ArrayList<>();
        LocalTime currentSlot = doctor.getShiftStartTime();

        while (currentSlot.isBefore(doctor.getShiftEndTime())) {
            // If this time slot is NOT in the booked times list, it is available!
            if (!bookedTimes.contains(currentSlot) && targetDate.atTime(currentSlot).isAfter(LocalDateTime.now())) {
                availableSlots.add(currentSlot);
            }
            // Move to the next slot (e.g., add 30 minutes)
            currentSlot = currentSlot.plusMinutes(doctor.getSlotDurationMinutes());
        }

        return availableSlots;
    }

    /**
     * Creates a new pending appointment request for a patient.
     *
     * @param request the {@link AppointmentRequest} containing details
     * @param requesterEmail the email of the user making the request
     * @return the saved appointment, as an {@link AppointmentDTO}
     */
    @Transactional
    public AppointmentDTO createAppointment(AppointmentRequest request, String requesterEmail) {
        Login user = loginRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("User not found"));

        PatientProfile patient = patientProfileRepository.findByUser(user)
                .orElseThrow(() -> new com.securehealth.backend.exception.BadRequestException("400: Please complete your patient profile before booking."));

        Login doctor = loginRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Doctor not found"));
        if (doctor.getRole() != com.securehealth.backend.model.Role.DOCTOR) {
            throw new com.securehealth.backend.exception.NotFoundException("404: Doctor not found");
        }
        requireBookable(doctor.getUserId(), request.getAppointmentDate());

        // UPDATE: Check against both CANCELLED and REJECTED statuses
        boolean isSlotTaken = appointmentRepository.existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
                doctor.getUserId(),
                request.getAppointmentDate(),
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.REJECTED));

        if (isSlotTaken) {
            throw new com.securehealth.backend.exception.ConflictException("409 Conflict: This time slot is currently unavailable or pending review.");
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setReasonForVisit(request.getReasonForVisit());
        appointment.setAppointmentType(request.getAppointmentType());
        appointment.setSpecialRequirements(request.getSpecialRequirements());

        // UPDATE: Set to PENDING instead of SCHEDULED
        appointment.setStatus(AppointmentStatus.PENDING_APPROVAL);

        // The availability check above is only a fast path: two requests can pass it
        // simultaneously. The UNIQUE active_slot_key column is what actually prevents
        // a double booking, and surfaces here as a constraint violation.
        try {
            return toDTO(appointmentRepository.saveAndFlush(appointment));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new com.securehealth.backend.exception.ConflictException("409 Conflict: This time slot is currently unavailable or pending review.");
        }
    }

    /**
     * ADMIN ONLY: Approves a pending appointment request.
     */
    @Transactional
    public AppointmentDTO approveAppointment(Long appointmentId) {
        return approveAppointment(appointmentId, null);
    }

    @Transactional
    public AppointmentDTO approveAppointment(Long appointmentId, String adminEmail) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL) {
            throw new com.securehealth.backend.exception.BadRequestException("400: Only pending appointments can be approved.");
        }

        appointment.setStatus(AppointmentStatus.SCHEDULED);
        AppointmentDTO result = toDTO(appointmentRepository.save(appointment));
        if (adminEmail != null) {
            auditService.record(adminEmail, "APPOINTMENT_APPROVED", "appointment=" + appointmentId);
        }
        notificationService.notify(appointment.getPatient() == null ? null : appointment.getPatient().getUser(),
                "APPOINTMENT_APPROVED", "Appointment approved",
                "Your appointment on " + appointment.getAppointmentDate() + " has been approved.");
        return result;
    }

    /**
     * ADMIN ONLY: Rejects a pending appointment request, freeing up the slot.
     */
    @Transactional
    public AppointmentDTO rejectAppointment(Long appointmentId, String rejectionReason) {
        return rejectAppointment(appointmentId, rejectionReason, null);
    }

    @Transactional
    public AppointmentDTO rejectAppointment(Long appointmentId, String rejectionReason, String adminEmail) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL) {
            throw new com.securehealth.backend.exception.BadRequestException("400: Only pending appointments can be rejected.");
        }

        appointment.setStatus(AppointmentStatus.REJECTED);
        if (rejectionReason != null && !rejectionReason.isBlank()) {
            appointment.setCancellationReason(rejectionReason);
        }
        AppointmentDTO result = toDTO(appointmentRepository.save(appointment));
        if (adminEmail != null) {
            auditService.record(adminEmail, "APPOINTMENT_REJECTED", "appointment=" + appointmentId);
        }
        notificationService.notify(appointment.getPatient() == null ? null : appointment.getPatient().getUser(),
                "APPOINTMENT_REJECTED", "Appointment request declined",
                "Your appointment request for " + appointment.getAppointmentDate() + " was not accepted.");
        return result;
    }

    /**
     * Retrieves all appointments currently pending administrative approval.
     *
     * @return a list of {@link AppointmentDTO} objects
     */
    @Transactional(readOnly = true)
    public List<AppointmentDTO> getPendingAppointments() {
        return appointmentRepository.findByStatus(AppointmentStatus.PENDING_APPROVAL).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single appointment by ID.
     *
     * @param id the appointment ID
     * @return the {@link AppointmentDTO}, or {@code null} if not found
     */
    @Transactional(readOnly = true)
    public AppointmentDTO getAppointmentById(Long id) {
        return appointmentRepository.findById(id).map(this::toDTO).orElse(null);
    }

    /**
     * Marks an appointment as completed by the attending doctor.
     *
     * @param id the ID of the appointment
     * @param doctorEmail the email of the doctor performing the action
     * @return the updated {@link AppointmentDTO}
     */
    @Transactional
    public AppointmentDTO completeAppointment(Long id, String doctorEmail) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));

        // Verify it belongs to the doctor requesting it
        if (!appointment.getDoctor().getEmail().equals(doctorEmail)) {
             throw new com.securehealth.backend.exception.ForbiddenException("403: You can only complete your own appointments.");
        }

        requireTransition(appointment, AppointmentStatus.COMPLETED);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        return toDTO(appointmentRepository.save(appointment));
    }

    /** DOCTOR ONLY: marks a scheduled appointment whose time has passed as a no-show. */
    @Transactional
    public AppointmentDTO markNoShow(Long id, String doctorEmail) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));
        if (!appointment.getDoctor().getEmail().equals(doctorEmail)) {
            throw new com.securehealth.backend.exception.ForbiddenException("403: You can only update your own appointments.");
        }
        requireTransition(appointment, AppointmentStatus.NO_SHOW);
        if (appointment.getAppointmentDate().isAfter(LocalDateTime.now())) {
            throw new com.securehealth.backend.exception.BadRequestException("400: A future appointment cannot be marked as a no-show.");
        }
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        return toDTO(appointmentRepository.save(appointment));
    }

    /**
     * DOCTOR ONLY: Updates appointment details (date/time, reason, doctor's notes).
     * Patients use {@link #rescheduleAppointment(Long, LocalDateTime, String)} instead,
     * which is restricted to the date/time field only.
     *
     * @param id the ID of the appointment
     * @param request the {@link AppointmentDTO} with updated details
     * @param doctorEmail the email of the doctor performing the update
     * @return the updated {@link AppointmentDTO}
     */
    @Transactional
    public AppointmentDTO updateAppointment(Long id, AppointmentDTO request, String doctorEmail) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));

        // Verify it belongs to the doctor requesting it
        if (!appointment.getDoctor().getEmail().equals(doctorEmail)) {
             throw new com.securehealth.backend.exception.ForbiddenException("403: You can only update your own appointments.");
        }

        if (request.getAppointmentDate() != null
                && !request.getAppointmentDate().equals(appointment.getAppointmentDate())) {
            if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL
                    && appointment.getStatus() != AppointmentStatus.SCHEDULED) {
                throw new com.securehealth.backend.exception.BadRequestException("400: A closed appointment cannot be moved.");
            }
            requireBookable(appointment.getDoctor().getUserId(), request.getAppointmentDate());
            boolean taken = appointmentRepository.existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
                    appointment.getDoctor().getUserId(), request.getAppointmentDate(), FREE_STATUSES);
            if (taken) {
                throw new com.securehealth.backend.exception.ConflictException("409 Conflict: This time slot is currently unavailable or pending review.");
            }
            appointment.setAppointmentDate(request.getAppointmentDate());
        }
        if (request.getDoctorNotes() != null) {
            appointment.setDoctorNotes(request.getDoctorNotes());
        }

        try {
            return toDTO(appointmentRepository.saveAndFlush(appointment));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new com.securehealth.backend.exception.ConflictException("409 Conflict: This time slot is currently unavailable or pending review.");
        }
    }

    /**
     * PATIENT ONLY: Reschedules the patient's own appointment to a new date/time.
     * <p>
     * Unlike {@link #updateAppointment}, this cannot touch doctor-authored fields
     * (notes, reason) and resets the appointment back to {@code PENDING_APPROVAL}
     * since the new slot has not yet been confirmed by staff.
     * </p>
     *
     * @param id                the ID of the appointment
     * @param newAppointmentDate the requested new date/time
     * @param patientEmail      the email of the patient requesting the reschedule
     * @return the updated {@link AppointmentDTO}
     * @throws RuntimeException if the appointment is not found, not owned by this patient,
     *                          or already completed/cancelled
     */
    @Transactional
    public AppointmentDTO rescheduleAppointment(Long id, LocalDateTime newAppointmentDate, String patientEmail) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));

        if (!appointment.getPatient().getUser().getEmail().equals(patientEmail)) {
            throw new com.securehealth.backend.exception.ForbiddenException("403: You can only reschedule your own appointments.");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL
                && appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new com.securehealth.backend.exception.BadRequestException("400: This appointment can no longer be rescheduled.");
        }
        requireBookable(appointment.getDoctor().getUserId(), newAppointmentDate);

        boolean isSlotTaken = appointmentRepository.existsByDoctor_UserIdAndAppointmentDateAndStatusNotIn(
                appointment.getDoctor().getUserId(),
                newAppointmentDate,
                FREE_STATUSES);
        if (isSlotTaken) {
            throw new com.securehealth.backend.exception.ConflictException("409 Conflict: This time slot is currently unavailable or pending review.");
        }

        appointment.setAppointmentDate(newAppointmentDate);
        appointment.setStatus(AppointmentStatus.PENDING_APPROVAL);

        try {
            return toDTO(appointmentRepository.saveAndFlush(appointment));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new com.securehealth.backend.exception.ConflictException("409 Conflict: This time slot is currently unavailable or pending review.");
        }
    }

    /**
     * Cancels an existing appointment.
     *
     * @param id the ID of the appointment to cancel
     * @param requesterEmail the email of the user cancelling the appointment
     * @param role the role of the requester (ADMIN, DOCTOR, or PATIENT)
     * @param reason optional free-text reason supplied by the requester
     * @return the cancelled {@link AppointmentDTO}
     */
    @Transactional
    public AppointmentDTO cancelAppointment(Long id, String requesterEmail, String role, String reason) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("404: Appointment not found"));

        if (!role.equals("ADMIN") &&
            !appointment.getDoctor().getEmail().equals(requesterEmail) &&
            !appointment.getPatient().getUser().getEmail().equals(requesterEmail)) {
             throw new com.securehealth.backend.exception.ForbiddenException("403: You can only cancel your own appointments.");
        }

        requireTransition(appointment, AppointmentStatus.CANCELLED);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            appointment.setCancellationReason(reason);
        }
        return toDTO(appointmentRepository.save(appointment));
    }

    /**
     * Permanently deletes an appointment from the system.
     *
     * @param id the ID of the appointment to delete
     */
    @Transactional
    public void deleteAppointment(Long id) {
        if (!appointmentRepository.existsById(id)) {
            throw new com.securehealth.backend.exception.NotFoundException("404: Appointment not found");
        }
        appointmentRepository.deleteById(id);
    }

    /**
     * Retrieves all appointments for a specific doctor.
     *
     * @param doctorId the ID of the doctor
     * @return a list of {@link AppointmentDTO} objects
     */
    @Transactional(readOnly = true)
    public List<AppointmentDTO> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctor_UserIdOrderByAppointmentDateAsc(doctorId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all appointments for a specific patient, most recent first.
     *
     * @param patientId the ID of the patient
     * @return a list of {@link AppointmentDTO} objects
     */
    @Transactional(readOnly = true)
    public List<AppointmentDTO> getAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findByPatient_ProfileIdOrderByAppointmentDateDesc(patientId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a list of all appointments in the system.
     *
     * @return a list of {@link AppointmentDTO} objects
     */
    @Transactional(readOnly = true)
    public List<AppointmentDTO> getAllAppointments() {
        return getAllAppointments(0, PageParams.DEFAULT_SIZE);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDTO> getAllAppointments(int page, int size) {
        return appointmentRepository.findAll(PageParams.of(page, size,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "appointmentDate")))
                .getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all appointments with the given status.
     *
     * @param status the status to filter by
     * @return a list of {@link AppointmentDTO} objects
     */
    @Transactional(readOnly = true)
    public List<AppointmentDTO> getAppointmentsByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
