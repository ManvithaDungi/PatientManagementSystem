package com.securehealth.backend.integration;

import com.securehealth.backend.model.*;
import com.securehealth.backend.repository.*;
import com.securehealth.backend.service.EmailService;
import com.securehealth.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the P0 findings of the Calyx audit (credential leaks, consent expiry, consent-free reads). */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditp0db",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class AuditP0RegressionTest {

    @Autowired MockMvc mvc;
    @Autowired LoginRepository logins;
    @Autowired PatientProfileRepository patients;
    @Autowired ConsentRepository consents;
    @Autowired AppointmentRepository appointments;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired HandoverNoteRepository handovers;
    @Autowired JwtUtil jwt;
    @MockBean EmailService emailService;

    Login nurse, nurse2, doctor, otherDoctor;
    PatientProfile patient;

    Login mk(String email, Role r) {
        Login l = new Login();
        l.setEmail(email);
        l.setPasswordHash("$argon2id$SENTINEL_HASH");
        l.setOtp("424242");
        l.setRole(r);
        return logins.saveAndFlush(l);
    }

    String bearer(Login u) {
        return "Bearer " + jwt.generateAccessToken(u.getEmail(), u.getRole().name(), u.getUserId());
    }

    String body(RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse().getContentAsString();
    }

    @BeforeEach
    void setUp() {
        nurse = mk("n1@t.test", Role.NURSE);
        nurse2 = mk("n2@t.test", Role.NURSE);
        doctor = mk("d1@t.test", Role.DOCTOR);
        otherDoctor = mk("d2@t.test", Role.DOCTOR);
        Login pu = mk("p1@t.test", Role.PATIENT);
        patient = new PatientProfile();
        patient.setUser(pu);
        patient.setFirstName("Pat");
        patient.setLastName("Ient");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patient.setAddress("1 Secret Street");
        patient.setAssignedNurse(nurse);
        patient.setAssignedDoctor(doctor);
        patient = patients.saveAndFlush(patient);
    }

    private void assertNoSecrets(String json) {
        assertFalse(json.contains("SENTINEL_HASH"), "passwordHash leaked: " + json);
        assertFalse(json.contains("424242"), "otp leaked: " + json);
        assertFalse(json.contains("passwordHash"), "passwordHash key leaked");
    }

    @Test
    void nurseAssignedPatients_doesNotLeakCredentials() throws Exception {
        assertNoSecrets(body(get("/api/nurse/assigned-patients").header("Authorization", bearer(nurse))));
    }

    @Test
    void vitalSignPost_doesNotLeakCredentials() throws Exception {
        String req = "{\"patientId\":" + patient.getProfileId()
                + ",\"bloodPressure\":\"120/80\",\"heartRate\":70,\"temperature\":98.6}";
        assertNoSecrets(body(post("/api/vital-signs").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON).content(req)));
    }

    @Test
    void handoverNotes_doNotLeakAuthorCredentials() throws Exception {
        HandoverNote n = new HandoverNote();
        n.setAuthor(nurse);
        n.setContent("note");
        n.setType("general");
        n.setPriority("normal");
        n.setShiftDirection("FOR_NEXT");
        handovers.saveAndFlush(n);
        assertNoSecrets(body(get("/api/nurse/handover").header("Authorization", bearer(nurse2))));
    }

    private void rx() {
        Prescription rx = new Prescription();
        rx.setPatient(patient);
        rx.setDoctor(doctor);
        rx.setMedicationName("Amoxicillin");
        rx.setDosage("500mg");
        rx.setFrequency("BID");
        rx.setDuration("7d");
        prescriptions.saveAndFlush(rx);
    }

    private void consent(Login provider, LocalDateTime expiresAt) {
        Consent c = new Consent();
        c.setPatient(patient);
        c.setGrantedTo(provider);
        c.setConsentType("PRESCRIPTIONS");
        c.setStatus("ACTIVE");
        c.setExpiresAt(expiresAt);
        consents.saveAndFlush(c);
    }

    @Test
    void expiredConsent_isDenied_andValidOrOpenEndedConsentIsAllowed() throws Exception {
        rx();
        consent(otherDoctor, LocalDateTime.now().minusDays(1));
        mvc.perform(get("/api/prescriptions/patient/" + patient.getProfileId())
                .header("Authorization", bearer(otherDoctor))).andExpect(status().isForbidden());

        Login third = mk("d3@t.test", Role.DOCTOR);
        consent(third, LocalDateTime.now().plusDays(1));
        mvc.perform(get("/api/prescriptions/patient/" + patient.getProfileId())
                .header("Authorization", bearer(third))).andExpect(status().isOk());

        Login fourth = mk("d4@t.test", Role.DOCTOR);
        consent(fourth, null);
        mvc.perform(get("/api/prescriptions/patient/" + patient.getProfileId())
                .header("Authorization", bearer(fourth))).andExpect(status().isOk());
    }

    @Test
    void patientDirectory_isScopedForDoctors() throws Exception {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(LocalDateTime.now().plusDays(2));
        a.setStatus(AppointmentStatus.SCHEDULED);
        appointments.saveAndFlush(a);

        String unrelated = body(get("/api/patients").header("Authorization", bearer(otherDoctor)));
        assertFalse(unrelated.contains("1 Secret Street"), unrelated);
        String own = body(get("/api/patients").header("Authorization", bearer(doctor)));
        assertTrue(own.contains("1 Secret Street"), own);
    }

    @Test
    void appointments_notReadableByUnrelatedProviders() throws Exception {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(LocalDateTime.now().plusDays(2));
        a.setStatus(AppointmentStatus.SCHEDULED);
        a.setDoctorNotes("Confidential clinician note");
        a = appointments.saveAndFlush(a);

        String pid = String.valueOf(patient.getProfileId());
        assertFalse(body(get("/api/appointments/patient/" + pid).header("Authorization", bearer(otherDoctor)))
                .contains("Confidential"));
        assertFalse(body(get("/api/appointments").header("Authorization", bearer(otherDoctor)))
                .contains("Confidential"));
        mvc.perform(get("/api/appointments/" + a.getAppointmentId()).header("Authorization", bearer(otherDoctor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/patient/" + pid).header("Authorization", bearer(nurse)))
                .andExpect(status().isForbidden());
        assertTrue(body(get("/api/appointments/patient/" + pid).header("Authorization", bearer(doctor)))
                .contains("Confidential"));
    }
}
