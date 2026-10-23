package com.securehealth.backend.repository;

import com.securehealth.backend.model.Appointment;
import com.securehealth.backend.model.AppointmentStatus;
import com.securehealth.backend.model.Consent;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that the database itself — not just the service-level pre-check —
 * rejects a double booking and a duplicate active consent, and that cancelled /
 * revoked rows correctly free the slot.
 */
@DataJpaTest
class UniquenessConstraintsTest {

    @Autowired private TestEntityManager em;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ConsentRepository consentRepository;

    private Login doctor;
    private PatientProfile patient;
    private final LocalDateTime slot = LocalDateTime.of(2030, 1, 15, 10, 0);

    @BeforeEach
    void setUp() {
        doctor = persistLogin("dr@hospital.test", Role.DOCTOR);
        Login patientLogin = persistLogin("pat@hospital.test", Role.PATIENT);

        patient = new PatientProfile();
        patient.setUser(patientLogin);
        patient.setFirstName("Pat");
        patient.setLastName("Ient");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        em.persistAndFlush(patient);
    }

    private Login persistLogin(String email, Role role) {
        Login l = new Login();
        l.setEmail(email);
        l.setPasswordHash("x");
        l.setRole(role);
        return em.persistAndFlush(l);
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setDoctor(doctor);
        a.setPatient(patient);
        a.setAppointmentDate(slot);
        a.setStatus(status);
        return a;
    }

    private Consent consent(String type, String status) {
        Consent c = new Consent();
        c.setPatient(patient);
        c.setGrantedTo(doctor);
        c.setConsentType(type);
        c.setStatus(status);
        return c;
    }

    // -------------------------------------------------------------- bookings

    @Test
    void secondLiveBookingForSameDoctorAndTimeIsRejectedByTheDatabase() {
        appointmentRepository.saveAndFlush(appointment(AppointmentStatus.PENDING_APPROVAL));

        assertThrows(DataIntegrityViolationException.class,
                () -> appointmentRepository.saveAndFlush(appointment(AppointmentStatus.SCHEDULED)));
    }

    @Test
    void cancelledAndRejectedBookingsDoNotBlockTheSlot() {
        appointmentRepository.saveAndFlush(appointment(AppointmentStatus.CANCELLED));
        appointmentRepository.saveAndFlush(appointment(AppointmentStatus.REJECTED));
        assertDoesNotThrow(() -> appointmentRepository.saveAndFlush(appointment(AppointmentStatus.PENDING_APPROVAL)));
    }

    @Test
    void cancellingFreesTheSlotForSomeoneElse() {
        Appointment first = appointmentRepository.saveAndFlush(appointment(AppointmentStatus.SCHEDULED));

        first.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.saveAndFlush(first);

        assertDoesNotThrow(() -> appointmentRepository.saveAndFlush(appointment(AppointmentStatus.PENDING_APPROVAL)));
    }

    @Test
    void movingAnAppointmentOntoAnOccupiedSlotIsRejected() {
        appointmentRepository.saveAndFlush(appointment(AppointmentStatus.SCHEDULED));

        Appointment other = appointment(AppointmentStatus.SCHEDULED);
        other.setAppointmentDate(slot.plusHours(1));
        appointmentRepository.saveAndFlush(other);

        other.setAppointmentDate(slot);
        assertThrows(DataIntegrityViolationException.class, () -> appointmentRepository.saveAndFlush(other));
    }

    @Test
    void differentDoctorsMayShareTheSameTime() {
        appointmentRepository.saveAndFlush(appointment(AppointmentStatus.SCHEDULED));

        Login otherDoctor = persistLogin("dr2@hospital.test", Role.DOCTOR);
        Appointment a = appointment(AppointmentStatus.SCHEDULED);
        a.setDoctor(otherDoctor);
        assertDoesNotThrow(() -> appointmentRepository.saveAndFlush(a));
    }

    // -------------------------------------------------------------- consents

    @Test
    void duplicateActiveConsentIsRejectedByTheDatabase() {
        consentRepository.saveAndFlush(consent("ALL", "ACTIVE"));

        assertThrows(DataIntegrityViolationException.class,
                () -> consentRepository.saveAndFlush(consent("ALL", "ACTIVE")));
    }

    @Test
    void revokedHistoryDoesNotBlockANewGrant() {
        Consent first = consentRepository.saveAndFlush(consent("ALL", "ACTIVE"));
        first.setStatus("REVOKED");
        consentRepository.saveAndFlush(first);

        assertDoesNotThrow(() -> consentRepository.saveAndFlush(consent("ALL", "ACTIVE")));
        assertDoesNotThrow(() -> consentRepository.saveAndFlush(consent("ALL", "REVOKED")));
    }

    @Test
    void differentConsentTypesCoexist() {
        consentRepository.saveAndFlush(consent("LAB_RESULTS", "ACTIVE"));
        assertDoesNotThrow(() -> consentRepository.saveAndFlush(consent("PRESCRIPTIONS", "ACTIVE")));
    }
}
