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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the P2/P3 audit items that live in the API layer (CALYX-017 ... 029, 034, 035, 036, 039, 040). */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditp3apidb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class AuditP3ApiRegressionTest {

    @Autowired MockMvc mvc;
    @Autowired LoginRepository logins;
    @Autowired PatientProfileRepository patients;
    @Autowired DoctorProfileRepository doctorProfiles;
    @Autowired AppointmentRepository appointments;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired LabTestRepository labs;
    @Autowired MedicalRecordRepository records;
    @Autowired ConsentRepository consents;
    @Autowired UploadedFileRepository files;
    @Autowired VitalSignRepository vitals;
    @Autowired NotificationRepository notifications;
    @Autowired JwtUtil jwt;
    @MockBean EmailService emailService;

    String sfx;
    Login admin, doctor, stranger, nurse, lab, patientUser;
    PatientProfile patient;

    Login mk(String name, Role r) {
        Login l = new Login();
        l.setEmail(name + "-" + sfx + "@t.test");
        l.setPasswordHash("x");
        l.setRole(r);
        return logins.saveAndFlush(l);
    }

    String bearer(Login u) {
        return "Bearer " + jwt.generateAccessToken(u.getEmail(), u.getRole().name(), u.getUserId());
    }

    String call(RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse().getContentAsString();
    }

    @BeforeEach
    void setUp() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        admin = mk("admin", Role.ADMIN);
        doctor = mk("doctor", Role.DOCTOR);
        stranger = mk("stranger", Role.DOCTOR);
        nurse = mk("nurse", Role.NURSE);
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

        DoctorProfile schedule = new DoctorProfile();
        schedule.setUser(doctor);
        schedule.setFirstName("Doc");
        schedule.setLastName("Tor");
        schedule.setSpecialty("General");
        schedule.setShiftStartTime(LocalTime.of(9, 0));
        schedule.setShiftEndTime(LocalTime.of(17, 0));
        schedule.setSlotDurationMinutes(30);
        schedule.setWorkingDays(new java.util.ArrayList<>(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)));
        doctorProfiles.saveAndFlush(schedule);
    }

    private LocalDateTime nextWeekday(DayOfWeek day, int hour, int minute) {
        return LocalDate.now().with(TemporalAdjusters.next(day)).atTime(hour, minute);
    }

    private String book(LocalDateTime when) {
        return "{\"doctorId\":" + doctor.getUserId() + ",\"appointmentDate\":\"" + when
                + "\",\"reasonForVisit\":\"check\",\"appointmentType\":\"Consultation\"}";
    }

    // ------------------------------------------------------------ CALYX-017 access log
    @Test
    void accessLog_isBuiltFromRealReads_andOnlyForTheOwner() throws Exception {
        Prescription rx = new Prescription();
        rx.setPatient(patient);
        rx.setDoctor(doctor);
        rx.setMedicationName("X");
        rx.setDosage("1");
        rx.setFrequency("1");
        rx.setDuration("1");
        prescriptions.saveAndFlush(rx);

        mvc.perform(get("/api/prescriptions/patient/" + patient.getProfileId()).header("Authorization", bearer(doctor)))
                .andExpect(status().isOk());

        String mine = call(get("/api/consent/access-log").header("Authorization", bearer(patientUser)));
        assertTrue(mine.contains(doctor.getEmail()), mine);
        assertTrue(mine.contains("Viewed prescriptions"), mine);

        Login other = mk("otherpatient", Role.PATIENT);
        PatientProfile op = new PatientProfile();
        op.setUser(other);
        op.setFirstName("O");
        op.setLastName("P");
        op.setDateOfBirth(LocalDate.of(1980, 1, 1));
        patients.saveAndFlush(op);
        assertEquals("[]", call(get("/api/consent/access-log").header("Authorization", bearer(other))));
        mvc.perform(get("/api/consent/access-log").header("Authorization", bearer(doctor))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ CALYX-019 consent modify / revoke reason
    @Test
    void consentCanBeModifiedInPlace_andRevokedWithAReason() throws Exception {
        String created = call(post("/api/consent").header("Authorization", bearer(patientUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantedToId\":" + stranger.getUserId() + ",\"consentType\":\"LAB_RESULTS\"}"));
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        String url = "/api/consent/" + id;

        String future = LocalDateTime.now().plusDays(30).withNano(0).toString();
        mvc.perform(put(url).header("Authorization", bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"" + future + "\",\"reason\":\"for a second opinion\"}"))
                .andExpect(status().isOk());
        Consent saved = consents.findById(id).orElseThrow();
        assertNotNull(saved.getExpiresAt());
        assertEquals("for a second opinion", saved.getReason());
        assertEquals("ACTIVE", saved.getStatus());

        mvc.perform(put(url).header("Authorization", bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"2001-01-01T00:00:00\"}")).andExpect(status().isBadRequest());

        mvc.perform(put(url + "/revoke").header("Authorization", bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"no longer needed\"}")).andExpect(status().isOk());
        Consent revoked = consents.findById(id).orElseThrow();
        assertEquals("REVOKED", revoked.getStatus());
        assertEquals("no longer needed", revoked.getRevocationReason());

        mvc.perform(put(url).header("Authorization", bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ CALYX-020 prescription refills / status
    @Test
    void prescription_refillsAreStored_andStatusFollowsTheTransitionRules() throws Exception {
        String body = "{\"patientId\":" + patient.getProfileId()
                + ",\"medicationName\":\"Metformin\",\"dosage\":\"500mg\",\"frequency\":\"BID\",\"duration\":\"30d\",\"refillsAllowed\":2}";
        String created = call(post("/api/prescriptions").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
        assertTrue(created.contains("\"refillsRemaining\":2"), created);
        long id = Long.parseLong(created.replaceAll(".*\"prescriptionId\":(\\d+).*", "$1"));

        mvc.perform(put("/api/prescriptions/" + id + "/refill").header("Authorization", bearer(doctor)))
                .andExpect(status().isOk());
        assertEquals(1, prescriptions.findById(id).orElseThrow().getRefillsRemaining());

        mvc.perform(put("/api/prescriptions/" + id + "/status").header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/prescriptions/" + id + "/status").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/prescriptions/" + id + "/status").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        assertEquals("CANCELLED", prescriptions.findById(id).orElseThrow().getStatus());
        mvc.perform(put("/api/prescriptions/" + id + "/status").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ CALYX-021 notifications
    @Test
    void notifications_areCreatedByWorkflowEvents_andAreOwnerScoped() throws Exception {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(nextWeekday(DayOfWeek.MONDAY, 10, 0));
        a = appointments.saveAndFlush(a);

        mvc.perform(put("/api/appointments/" + a.getAppointmentId() + "/approve").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        String mine = call(get("/api/notifications").header("Authorization", bearer(patientUser)));
        assertTrue(mine.contains("\"unread\":1"), mine);
        assertTrue(mine.contains("Appointment approved"), mine);
        long nid = Long.parseLong(mine.replaceAll(".*\"items\":\\[\\{\"id\":(\\d+).*", "$1"));

        // nobody else can see or clear it
        assertTrue(call(get("/api/notifications").header("Authorization", bearer(doctor))).contains("\"unread\":0"));
        mvc.perform(put("/api/notifications/" + nid + "/read").header("Authorization", bearer(doctor)))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/notifications/" + nid + "/read").header("Authorization", bearer(patientUser)))
                .andExpect(status().isOk());
        assertTrue(call(get("/api/notifications").header("Authorization", bearer(patientUser))).contains("\"unread\":0"));
    }

    @Test
    void publishedLabResult_notifiesPatientAndOrderingDoctor() throws Exception {
        LabTest t = new LabTest();
        t.setPatient(patient);
        t.setOrderedBy(doctor);
        t.setTestName("CBC");
        t.setStatus("Collected");
        t = labs.saveAndFlush(t);

        mvc.perform(put("/api/lab-technician/orders/" + t.getTestId() + "/upload").header("Authorization", bearer(lab))
                .contentType(MediaType.APPLICATION_JSON).content("{\"resultValue\":\"5\"}")).andExpect(status().isOk());

        assertEquals(1, notifications.countByUser_UserIdAndReadFalse(patientUser.getUserId()));
        assertEquals(1, notifications.countByUser_UserIdAndReadFalse(doctor.getUserId()));
    }

    // ------------------------------------------------------------ CALYX-023 vitals extras
    @Test
    void vitalSigns_storePainNotesAndTemperatureRoute() throws Exception {
        String req = "{\"patientId\":" + patient.getProfileId() + ",\"bloodPressure\":\"120/80\",\"heartRate\":70,\"temperature\":98.6,"
                + "\"painLevel\":4,\"notes\":\"complains of headache\",\"temperatureRoute\":\"oral\"}";
        String body = call(post("/api/vital-signs").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON).content(req));
        assertTrue(body.contains("\"painLevel\":4") && body.contains("complains of headache") && body.contains("oral"), body);

        mvc.perform(post("/api/vital-signs").header("Authorization", bearer(nurse)).contentType(MediaType.APPLICATION_JSON)
                .content(req.replace("\"painLevel\":4", "\"painLevel\":11"))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ CALYX-024 appointment rules
    @Test
    void booking_enforcesFutureWorkingDayShiftAndSlotGrid() throws Exception {
        String h = "Authorization";
        mvc.perform(post("/api/appointments").header(h, bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content(book(LocalDateTime.now().minusDays(1)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/appointments").header(h, bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content(book(nextWeekday(DayOfWeek.SUNDAY, 10, 0)))).andExpect(status().isBadRequest());   // not a working day
        mvc.perform(post("/api/appointments").header(h, bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content(book(nextWeekday(DayOfWeek.MONDAY, 10, 17)))).andExpect(status().isBadRequest());  // off the 30-minute grid
        mvc.perform(post("/api/appointments").header(h, bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content(book(nextWeekday(DayOfWeek.MONDAY, 17, 0)))).andExpect(status().isBadRequest());   // after the shift
        mvc.perform(post("/api/appointments").header(h, bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content(book(nextWeekday(DayOfWeek.MONDAY, 10, 0)))).andExpect(status().isOk());
        mvc.perform(post("/api/appointments").header(h, bearer(patientUser)).contentType(MediaType.APPLICATION_JSON)
                .content(book(nextWeekday(DayOfWeek.MONDAY, 10, 0)))).andExpect(status().isConflict());
    }

    @Test
    void rejectedSlot_isFreeAgain_forBothSlotListAndBooking() throws Exception {
        LocalDateTime when = nextWeekday(DayOfWeek.TUESDAY, 11, 0);
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(when);
        a.setStatus(AppointmentStatus.REJECTED);
        appointments.saveAndFlush(a);

        String slots = call(get("/api/appointments/doctor/" + doctor.getUserId() + "/available-slots")
                .header("Authorization", bearer(patientUser)).param("date", when.toLocalDate().toString()));
        assertTrue(slots.contains("\"11:00:00\"") || slots.contains("[11,0]") || slots.contains("11:00"), slots);
        mvc.perform(post("/api/appointments").header("Authorization", bearer(patientUser))
                .contentType(MediaType.APPLICATION_JSON).content(book(when))).andExpect(status().isOk());
    }

    @Test
    void statusTransitions_areGuarded_andNoShowIsReachable() throws Exception {
        Appointment pending = new Appointment();
        pending.setPatient(patient);
        pending.setDoctor(doctor);
        pending.setAppointmentDate(nextWeekday(DayOfWeek.WEDNESDAY, 9, 0));
        pending = appointments.saveAndFlush(pending);

        // cannot complete something that was never approved
        mvc.perform(put("/api/appointments/" + pending.getAppointmentId() + "/complete").header("Authorization", bearer(doctor)))
                .andExpect(status().isBadRequest());

        Appointment done = new Appointment();
        done.setPatient(patient);
        done.setDoctor(doctor);
        done.setAppointmentDate(nextWeekday(DayOfWeek.WEDNESDAY, 9, 30));
        done.setStatus(AppointmentStatus.COMPLETED);
        done = appointments.saveAndFlush(done);
        mvc.perform(put("/api/appointments/" + done.getAppointmentId() + "/cancel").header("Authorization", bearer(patientUser)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/appointments/" + done.getAppointmentId() + "/reschedule").header("Authorization", bearer(patientUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentDate\":\"" + nextWeekday(DayOfWeek.THURSDAY, 9, 0) + "\"}")).andExpect(status().isBadRequest());

        // no-show: only a scheduled appointment whose time has passed
        Appointment future = new Appointment();
        future.setPatient(patient);
        future.setDoctor(doctor);
        future.setAppointmentDate(nextWeekday(DayOfWeek.THURSDAY, 9, 0));
        future.setStatus(AppointmentStatus.SCHEDULED);
        future = appointments.saveAndFlush(future);
        mvc.perform(put("/api/appointments/" + future.getAppointmentId() + "/no-show").header("Authorization", bearer(doctor)))
                .andExpect(status().isBadRequest());

        Appointment past = new Appointment();
        past.setPatient(patient);
        past.setDoctor(doctor);
        past.setAppointmentDate(LocalDateTime.now().minusDays(2).withNano(0));
        past.setStatus(AppointmentStatus.SCHEDULED);
        past = appointments.saveAndFlush(past);
        mvc.perform(put("/api/appointments/" + past.getAppointmentId() + "/no-show").header("Authorization", bearer(doctor)))
                .andExpect(status().isOk());
        assertEquals(AppointmentStatus.NO_SHOW, appointments.findById(past.getAppointmentId()).orElseThrow().getStatus());
    }

    @Test
    void doctorDateEdit_cannotLandOnATakenSlot() throws Exception {
        LocalDateTime t1 = nextWeekday(DayOfWeek.FRIDAY, 9, 0);
        LocalDateTime t2 = nextWeekday(DayOfWeek.FRIDAY, 9, 30);
        Appointment a1 = new Appointment();
        a1.setPatient(patient);
        a1.setDoctor(doctor);
        a1.setAppointmentDate(t1);
        a1.setStatus(AppointmentStatus.SCHEDULED);
        appointments.saveAndFlush(a1);
        Appointment a2 = new Appointment();
        a2.setPatient(patient);
        a2.setDoctor(doctor);
        a2.setAppointmentDate(t2);
        a2.setStatus(AppointmentStatus.SCHEDULED);
        a2 = appointments.saveAndFlush(a2);

        mvc.perform(put("/api/appointments/" + a2.getAppointmentId()).header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"appointmentDate\":\"" + t1 + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void doctorWithoutConfiguredShift_getsACleanBadRequestNotAnNpe() throws Exception {
        DoctorProfile p = doctorProfiles.findByUser(doctor).orElseThrow();
        p.setSlotDurationMinutes(0); // the columns are NOT NULL, so "not configured" means a zero-length slot
        doctorProfiles.saveAndFlush(p);
        mvc.perform(get("/api/appointments/doctor/" + doctor.getUserId() + "/available-slots")
                .header("Authorization", bearer(patientUser)).param("date", nextWeekday(DayOfWeek.MONDAY, 9, 0).toLocalDate().toString()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ CALYX-029 persistence gaps
    @Test
    void labOrder_persistsResultFields_andRecordsAcceptOnlyOwnAttachments() throws Exception {
        String pid = String.valueOf(patient.getProfileId());
        mvc.perform(post("/api/lab-results").header("Authorization", bearer(doctor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + pid + ",\"testName\":\"Glucose\",\"testCategory\":\"Chem\",\"resultValue\":\"5.4\","
                        + "\"unit\":\"mmol/L\",\"referenceRange\":\"4-6\"}")).andExpect(status().isOk());
        LabTest saved = labs.findByPatient_ProfileId(patient.getProfileId()).get(0);
        assertEquals("5.4", saved.getResultValue());
        assertEquals("mmol/L", saved.getUnit());
        assertEquals("4-6", saved.getReferenceRange());

        UploadedFile mine = new UploadedFile();
        mine.setFilename("mine-" + sfx + ".pdf.enc");
        mine.setUploadedBy(doctor);
        files.saveAndFlush(mine);
        UploadedFile theirs = new UploadedFile();
        theirs.setFilename("theirs-" + sfx + ".pdf.enc");
        theirs.setUploadedBy(stranger);
        files.saveAndFlush(theirs);

        String base = "{\"patientId\":" + pid + ",\"diagnosis\":\"d\",\"symptoms\":\"s\",\"treatmentProvided\":\"t\",\"attachmentUrl\":\"";
        mvc.perform(post("/api/medical-records").header("Authorization", bearer(doctor)).contentType(MediaType.APPLICATION_JSON)
                .content(base + theirs.getFilename() + "\"}")).andExpect(status().isBadRequest());
        String ok = call(post("/api/medical-records").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON).content(base + mine.getFilename() + "\"}"));
        assertTrue(ok.contains(mine.getFilename()), ok);
        assertEquals(mine.getFilename(), records.findByPatient_ProfileId(patient.getProfileId()).get(0).getAttachmentUrl());
    }

    // ------------------------------------------------------------ CALYX-035 reject reason
    @Test
    void rejectReason_isStoredWithoutJsonQuotes() throws Exception {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setAppointmentDate(nextWeekday(DayOfWeek.MONDAY, 12, 0));
        a = appointments.saveAndFlush(a);
        mvc.perform(put("/api/appointments/" + a.getAppointmentId() + "/reject").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Doctor unavailable\"}")).andExpect(status().isOk());
        assertEquals("Doctor unavailable", appointments.findById(a.getAppointmentId()).orElseThrow().getCancellationReason());
    }

    // ------------------------------------------------------------ CALYX-039 pagination and batch endpoints
    @Test
    void listEndpoints_honourPageAndSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            Appointment a = new Appointment();
            a.setPatient(patient);
            a.setDoctor(doctor);
            a.setAppointmentDate(nextWeekday(DayOfWeek.MONDAY, 9 + i, 0));
            appointments.saveAndFlush(a);
        }
        String one = call(get("/api/appointments").param("size", "1").header("Authorization", bearer(admin)));
        assertEquals(1, one.split("\"appointmentId\"").length - 1, one);
        String all = call(get("/api/appointments").header("Authorization", bearer(admin)));
        assertEquals(3, all.split("\"appointmentId\"").length - 1, all);
    }

    @Test
    void myPrescriptions_isOneRequest_andRespectsConsent() throws Exception {
        for (String drug : List.of("A", "B")) {
            Prescription rx = new Prescription();
            rx.setPatient(patient);
            rx.setDoctor(doctor);
            rx.setMedicationName(drug);
            rx.setDosage("1");
            rx.setFrequency("1");
            rx.setDuration("1");
            prescriptions.saveAndFlush(rx);
        }
        String mine = call(get("/api/prescriptions/mine").header("Authorization", bearer(doctor)));
        assertEquals(2, mine.split("\"prescriptionId\"").length - 1, mine);

        // the doctor is no longer in the patient's care team and holds no consent: nothing is returned
        patient.setAssignedDoctor(null);
        patients.saveAndFlush(patient);
        assertEquals("[]", call(get("/api/prescriptions/mine").header("Authorization", bearer(doctor))));
        mvc.perform(get("/api/prescriptions/mine").header("Authorization", bearer(nurse))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ CALYX-040 health without redis, Argon2 strength
    @Test
    void health_respondsWithoutRedisAndExposesNoRedisComponent() throws Exception {
        String body = call(get("/actuator/health"));
        assertTrue(body.contains("\"status\":\"UP\""), body);
        assertFalse(body.toLowerCase().contains("redis"), body);
    }

    // ------------------------------------------------------------ CALYX-034 no fabricated identity
    @Test
    void registration_requiresNameAndDateOfBirth() throws Exception {
        String pw = "Str0ng-Unusual-Phrase-91";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"noname-" + sfx + "@t.test\",\"password\":\"" + pw + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nodob-" + sfx + "@t.test\",\"password\":\"" + pw + "\",\"full_name\":\"Some One\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"future-" + sfx + "@t.test\",\"password\":\"" + pw + "\",\"full_name\":\"Some One\",\"date_of_birth\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ok-" + sfx + "@t.test\",\"password\":\"" + pw + "\",\"full_name\":\"Mary Jane Watson\",\"date_of_birth\":\"1990-03-04\"}"))
                .andExpect(status().isCreated());
        Login created = logins.findByEmail("ok-" + sfx + "@t.test").orElseThrow();
        PatientProfile profile = patients.findByUser(created).orElseThrow();
        assertEquals("Mary", profile.getFirstName());
        assertEquals("Jane Watson", profile.getLastName());
        assertEquals(LocalDate.of(1990, 3, 4), profile.getDateOfBirth());
    }
}
