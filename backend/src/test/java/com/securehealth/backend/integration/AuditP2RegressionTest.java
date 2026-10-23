package com.securehealth.backend.integration;

import com.securehealth.backend.exception.BadRequestException;
import com.securehealth.backend.model.*;
import com.securehealth.backend.repository.*;
import com.securehealth.backend.service.AdminService;
import com.securehealth.backend.service.EmailService;
import com.securehealth.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the remaining P1 audit items (CALYX-005, 006, 013, 014, 015, 016, 027). */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditp2db",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class AuditP2RegressionTest {

    @Autowired MockMvc mvc;
    @Autowired LoginRepository logins;
    @Autowired PatientProfileRepository patients;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired LabTestRepository labs;
    @Autowired UploadedFileRepository files;
    @Autowired AuditLogRepository audits;
    @Autowired AppointmentRepository appointments;
    @Autowired DoctorProfileRepository doctorProfiles;
    @Autowired AdminService adminService;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwt;
    @MockBean EmailService emailService;

    String sfx;
    Login admin, doctor, stranger, nurse, otherNurse, lab, patientUser;
    PatientProfile patient;

    Login mk(String name, Role r) {
        Login l = new Login();
        l.setEmail(name + "-" + sfx + "@t.test");
        l.setPasswordHash(encoder.encode("Str0ng-Unusual-Phrase-91"));
        l.setRole(r);
        return logins.saveAndFlush(l);
    }

    String bearer(Login u) {
        return "Bearer " + jwt.generateAccessToken(u.getEmail(), u.getRole().name(), u.getUserId());
    }

    String call(RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse().getContentAsString();
    }

    List<AuditLog> auditsFor(String action) {
        return audits.findAll().stream().filter(a -> action.equals(a.getAction())).toList();
    }

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        admin = mk("admin", Role.ADMIN);
        doctor = mk("doctor", Role.DOCTOR);
        stranger = mk("stranger", Role.DOCTOR);
        nurse = mk("nurse", Role.NURSE);
        otherNurse = mk("othernurse", Role.NURSE);
        lab = mk("lab", Role.LAB_TECHNICIAN);
        patientUser = mk("patient", Role.PATIENT);
        patient = new PatientProfile();
        patient.setUser(patientUser);
        patient.setFirstName("Pat");
        patient.setLastName("Ient");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patient.setAssignedDoctor(doctor);
        patient.setAssignedNurse(nurse);
        patient = patients.saveAndFlush(patient);
    }

    // ---------------------------------------------------------------- CALYX-016
    @Test
    void longDiagnosisSaves_overLimitIsCleanBadRequest_andAuditHasNoPhi() throws Exception {
        String pid = String.valueOf(patient.getProfileId());
        String ok = "{\"patientId\":" + pid + ",\"diagnosis\":\"" + "D".repeat(1000)
                + "\",\"symptoms\":\"s\",\"treatmentProvided\":\"t\"}";
        mvc.perform(post("/api/medical-records").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isOk());

        String tooLong = "{\"patientId\":" + pid + ",\"diagnosis\":\"" + "D".repeat(2001)
                + "\",\"symptoms\":\"s\",\"treatmentProvided\":\"t\"}";
        String body = call(post("/api/medical-records").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(tooLong));
        mvc.perform(post("/api/medical-records").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(tooLong)).andExpect(status().isBadRequest());
        assertFalse(body.contains("could not execute statement"), body);

        for (AuditLog a : auditsFor("MEDICAL_RECORD_CREATED")) {
            assertFalse(a.getDetails().contains("DDDD"), "audit details must not carry clinical content");
        }
    }

    // ---------------------------------------------------------------- CALYX-005
    private Prescription activeRx() {
        Prescription rx = new Prescription();
        rx.setPatient(patient);
        rx.setDoctor(doctor);
        rx.setMedicationName("Morphine");
        rx.setDosage("5mg");
        rx.setFrequency("QID");
        rx.setDuration("3d");
        return prescriptions.saveAndFlush(rx);
    }

    @Test
    void medicationAdministration_enforcesAssignment_status_interval_andAudits() throws Exception {
        Prescription rx = activeRx();
        String url = "/api/nurse/medications/" + rx.getPrescriptionId();

        mvc.perform(post(url + "/administer").header("Authorization", bearer(otherNurse))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get(url + "/history").header("Authorization", bearer(otherNurse)))
                .andExpect(status().isForbidden());

        mvc.perform(post(url + "/administer").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        assertEquals(1, auditsFor("MEDICATION_ADMINISTERED").size());

        mvc.perform(post(url + "/administer").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(get(url + "/history").header("Authorization", bearer(nurse))).andExpect(status().isOk());

        rx.setStatus("CANCELLED");
        prescriptions.saveAndFlush(rx);
        mvc.perform(post(url + "/administer").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- CALYX-006
    private LabTest labTest(String status) {
        LabTest t = new LabTest();
        t.setPatient(patient);
        t.setOrderedBy(doctor);
        t.setTestName("CBC");
        t.setStatus(status);
        return labs.saveAndFlush(t);
    }

    @Test
    void labResultsList_isScopedForDoctors() throws Exception {
        labTest("Pending");
        assertTrue(call(get("/api/lab-results").header("Authorization", bearer(doctor))).contains("CBC"));
        assertFalse(call(get("/api/lab-results").header("Authorization", bearer(stranger))).contains("CBC"));
        assertFalse(call(get("/api/lab-results/pending").header("Authorization", bearer(stranger))).contains("CBC"));
        assertTrue(call(get("/api/lab-results/pending").header("Authorization", bearer(lab))).contains("CBC"));
    }

    @Test
    void labUpload_acceptsOnlyOwnFile_persistsUnitAndRange_andIsAuditedAndFinal() throws Exception {
        LabTest t = labTest("Collected");
        String url = "/api/lab-technician/orders/" + t.getTestId() + "/upload";

        UploadedFile foreign = new UploadedFile();
        foreign.setFilename("foreign-" + sfx + ".pdf.enc");
        foreign.setUploadedBy(doctor);
        files.saveAndFlush(foreign);
        mvc.perform(put(url).header("Authorization", bearer(lab)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultValue\":\"5\",\"fileUrl\":\"" + foreign.getFilename() + "\"}"))
                .andExpect(status().isBadRequest());

        UploadedFile own = new UploadedFile();
        own.setFilename("own-" + sfx + ".pdf.enc");
        own.setUploadedBy(lab);
        files.saveAndFlush(own);
        mvc.perform(put(url).header("Authorization", bearer(lab)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultValue\":\"5\",\"unit\":\"g/dL\",\"referenceRange\":\"4-6\",\"fileUrl\":\""
                        + own.getFilename() + "\"}")).andExpect(status().isOk());

        LabTest saved = labs.findById(t.getTestId()).orElseThrow();
        assertEquals("g/dL", saved.getUnit());
        assertEquals("4-6", saved.getReferenceRange());
        assertEquals("Completed", saved.getStatus());
        assertEquals(1, auditsFor("LAB_RESULT_UPLOADED").size());

        mvc.perform(put(url).header("Authorization", bearer(lab)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultValue\":\"9\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void labStatusChange_isAudited() throws Exception {
        LabTest t = labTest("Pending");
        mvc.perform(put("/api/lab-technician/orders/" + t.getTestId() + "/status")
                .header("Authorization", bearer(lab)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"Collected\"}")).andExpect(status().isOk());
        assertEquals(1, auditsFor("LAB_STATUS_CHANGED").size());
    }

    // ---------------------------------------------------------------- CALYX-015
    @Test
    void clinicalWrites_requireRelationshipOrConsent_andAdminCannotWrite() throws Exception {
        String pid = String.valueOf(patient.getProfileId());
        String record = "{\"patientId\":" + pid + ",\"diagnosis\":\"d\",\"symptoms\":\"s\",\"treatmentProvided\":\"t\"}";
        String rx = "{\"patientId\":" + pid + ",\"medicationName\":\"m\",\"dosage\":\"1\",\"frequency\":\"1\",\"duration\":\"1\"}";
        String labOrder = "{\"patientId\":" + pid + ",\"testName\":\"t\",\"testCategory\":\"c\"}";
        String vitals = "{\"patientId\":" + pid + ",\"bloodPressure\":\"120/80\",\"heartRate\":70,\"temperature\":98.6}";

        mvc.perform(post("/api/medical-records").header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON).content(record)).andExpect(status().isForbidden());
        mvc.perform(post("/api/prescriptions").header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON).content(rx)).andExpect(status().isForbidden());
        mvc.perform(post("/api/lab-results").header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON).content(labOrder)).andExpect(status().isForbidden());
        mvc.perform(post("/api/vital-signs").header("Authorization", bearer(otherNurse))
                .contentType(MediaType.APPLICATION_JSON).content(vitals)).andExpect(status().isForbidden());

        mvc.perform(post("/api/vital-signs").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(vitals)).andExpect(status().isForbidden());
        mvc.perform(post("/api/lab-results").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(labOrder)).andExpect(status().isForbidden());

        // the treating team still can
        mvc.perform(post("/api/medical-records").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(record)).andExpect(status().isOk());
        mvc.perform(post("/api/vital-signs").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON).content(vitals)).andExpect(status().isOk());
        mvc.perform(post("/api/lab-results").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(labOrder)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- CALYX-013
    @Test
    void staffRemoval_isSoftDisable_withGuards_andAudited() throws Exception {
        mvc.perform(delete("/api/admin/staff/" + patientUser.getUserId()).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        assertTrue(logins.existsById(patientUser.getUserId()));

        mvc.perform(delete("/api/admin/staff/" + admin.getUserId()).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/staff/" + nurse.getUserId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        Login disabled = logins.findById(nurse.getUserId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(disabled.getArchived()), "account is kept but flagged disabled");
        assertEquals(1, auditsFor("STAFF_DISABLED").size());

        // a live token of a disabled user is rejected
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(nurse))).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/admin/staff/" + nurse.getUserId() + "/enable").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(nurse))).andExpect(status().isOk());
    }

    @Test
    void lastActiveAdmin_cannotBeRemovedOrDemoted() {
        assertThrows(BadRequestException.class, () -> adminService.removeStaffMember(admin.getUserId(), "someone-else@t.test"));
        assertThrows(BadRequestException.class, () -> adminService.updateStaffRole(admin.getUserId(), "NURSE", "someone-else@t.test"));
    }

    @Test
    void roleChange_rules() throws Exception {
        String roleUrl = "/api/admin/staff/" + nurse.getUserId() + "/role";
        String bogus = call(put(roleUrl).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newRole\":\"SUPERUSER\"}"));
        assertFalse(bogus.contains("No enum constant"), bogus);
        mvc.perform(put(roleUrl).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newRole\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(roleUrl).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newRole\":\"PATIENT\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(roleUrl).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newRole\":\"DOCTOR\"}"))
                .andExpect(status().isOk());
        Login promoted = logins.findById(nurse.getUserId()).orElseThrow();
        assertEquals(Role.DOCTOR, promoted.getRole());
        assertTrue(promoted.isTwoFactorEnabled(), "2FA is forced on for doctors/admins");
        assertTrue(doctorProfiles.findByUser(promoted).isPresent(), "an empty doctor profile is created");
        assertEquals(1, auditsFor("STAFF_ROLE_CHANGED").size());

        mvc.perform(put("/api/admin/staff/" + admin.getUserId() + "/role").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"newRole\":\"DOCTOR\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- CALYX-014
    @Test
    void consentAndAppointmentAndReadEvents_areAudited() throws Exception {
        String grant = "{\"grantedToId\":" + stranger.getUserId() + ",\"consentType\":\"PRESCRIPTIONS\"}";
        String created = call(post("/api/consent").header("Authorization", bearer(patientUser))
                .contentType(MediaType.APPLICATION_JSON).content(grant));
        assertEquals(1, auditsFor("CONSENT_GRANTED").size(), created);
        long consentId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(put("/api/consent/" + consentId + "/revoke").header("Authorization", bearer(patientUser)))
                .andExpect(status().isOk());
        assertEquals(1, auditsFor("CONSENT_REVOKED").size());

        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(LocalDateTime.now().plusDays(3));
        a = appointments.saveAndFlush(a);
        mvc.perform(put("/api/appointments/" + a.getAppointmentId() + "/approve").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertEquals(1, auditsFor("APPOINTMENT_APPROVED").size());

        activeRx();
        mvc.perform(get("/api/prescriptions/patient/" + patient.getProfileId()).header("Authorization", bearer(doctor)))
                .andExpect(status().isOk());
        assertEquals(1, auditsFor("PATIENT_DATA_READ").size());
    }

    // ---------------------------------------------------------------- CALYX-027
    @Test
    void deletedUserWithLiveToken_gets401Not500() throws Exception {
        Login ghost = mk("ghost", Role.PATIENT);
        String token = bearer(ghost);
        logins.delete(ghost);
        logins.flush();
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLogin_recordsLastLoginAt_andDisabledAccountsCannotLogin() throws Exception {
        String body = "{\"email\":\"" + patientUser.getEmail() + "\",\"password\":\"Str0ng-Unusual-Phrase-91\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertNotNull(logins.findById(patientUser.getUserId()).orElseThrow().getLastLoginAt());

        Login u = logins.findById(patientUser.getUserId()).orElseThrow();
        u.setArchived(true);
        logins.saveAndFlush(u);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
