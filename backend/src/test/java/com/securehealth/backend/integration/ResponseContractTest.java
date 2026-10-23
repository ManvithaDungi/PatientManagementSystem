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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Contract test: no JSON response produced by any GET endpoint (called as every role, against realistic
 * seeded data) and by the write endpoints that echo entities may contain credential fields.
 */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:contractdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class ResponseContractTest {

    private static final List<String> FORBIDDEN = List.of(
            "passwordHash", "SENTINEL_SECRET", "SENTINEL_OTP", "\"otp\"", "otpExpiry", "failedAttempts", "lockoutUntil");

    @Autowired MockMvc mvc;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired LoginRepository logins;
    @Autowired PatientProfileRepository patients;
    @Autowired ConsentRepository consents;
    @Autowired AppointmentRepository appointments;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired MedicalRecordRepository records;
    @Autowired LabTestRepository labs;
    @Autowired VitalSignRepository vitals;
    @Autowired HandoverNoteRepository handovers;
    @Autowired NurseTaskRepository tasks;
    @Autowired JwtUtil jwt;
    @MockBean EmailService emailService;

    Map<Role, Login> byRole = new LinkedHashMap<>();
    PatientProfile patient;

    Login mk(String email, Role r) {
        Login l = new Login();
        l.setEmail(email);
        l.setPasswordHash("SENTINEL_SECRET_" + email);
        l.setOtp("SENTINEL_OTP");
        l.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        l.setRole(r);
        return logins.saveAndFlush(l);
    }

    String bearer(Login u) {
        return "Bearer " + jwt.generateAccessToken(u.getEmail(), u.getRole().name(), u.getUserId());
    }

    @BeforeEach
    void seed() {
        for (Role r : Role.values()) {
            byRole.put(r, mk(r.name().toLowerCase() + "@c.test", r));
        }
        patient = new PatientProfile();
        patient.setUser(byRole.get(Role.PATIENT));
        patient.setFirstName("Pat");
        patient.setLastName("Ient");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patient.setAssignedDoctor(byRole.get(Role.DOCTOR));
        patient.setAssignedNurse(byRole.get(Role.NURSE));
        patient = patients.saveAndFlush(patient);

        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(byRole.get(Role.DOCTOR));
        a.setAppointmentDate(LocalDateTime.now().plusDays(1));
        a.setStatus(AppointmentStatus.SCHEDULED);
        appointments.saveAndFlush(a);

        Prescription rx = new Prescription();
        rx.setPatient(patient);
        rx.setDoctor(byRole.get(Role.DOCTOR));
        rx.setMedicationName("X");
        rx.setDosage("1");
        rx.setFrequency("1");
        rx.setDuration("1");
        prescriptions.saveAndFlush(rx);

        MedicalRecord mr = new MedicalRecord();
        mr.setPatient(patient);
        mr.setDoctor(byRole.get(Role.DOCTOR));
        mr.setDiagnosis("D");
        records.saveAndFlush(mr);

        LabTest lt = new LabTest();
        lt.setPatient(patient);
        lt.setOrderedBy(byRole.get(Role.DOCTOR));
        lt.setTestName("CBC");
        lt.setStatus("Pending");
        labs.saveAndFlush(lt);

        VitalSign vs = new VitalSign();
        vs.setPatient(patient);
        vs.setNurse(byRole.get(Role.NURSE));
        vs.setBloodPressure("120/80");
        vitals.saveAndFlush(vs);

        HandoverNote n = new HandoverNote();
        n.setAuthor(byRole.get(Role.NURSE));
        n.setPatient(patient);
        n.setContent("c");
        n.setShiftDirection("FOR_NEXT");
        handovers.saveAndFlush(n);

        NurseTask t = new NurseTask();
        t.setAssignedNurse(byRole.get(Role.NURSE));
        t.setPatient(patient);
        t.setTitle("t");
        t.setCategory("general");
        t.setPriority("medium");
        t.setDueTime(LocalDateTime.now().plusHours(1));
        tasks.saveAndFlush(t);

        Consent c = new Consent();
        c.setPatient(patient);
        c.setGrantedTo(byRole.get(Role.LAB_TECHNICIAN));
        c.setConsentType("ALL");
        c.setStatus("ACTIVE");
        consents.saveAndFlush(c);
    }

    private void assertClean(String label, String body) {
        for (String bad : FORBIDDEN) {
            assertFalse(body.contains(bad), label + " leaks '" + bad + "': " + body);
        }
    }

    @Test
    void everyGetEndpoint_neverReturnsCredentialFields_forAnyRole() throws Exception {
        List<String> paths = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            String beanPkg = e.getValue().getBeanType().getPackageName();
            if (!beanPkg.startsWith("com.securehealth")) continue;
            if (!e.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET)) continue;
            Set<String> patterns = e.getKey().getPathPatternsCondition().getPatternValues();
            for (String pattern : patterns) {
                paths.add(pattern.replaceAll("\\{[^/}]+}", "1"));
            }
        }
        assertTrue(paths.size() > 30, "expected to discover the GET endpoints, found " + paths.size());

        int calls = 0;
        for (Login user : byRole.values()) {
            for (String path : paths) {
                String url = path.contains("available-slots") ? path + "?date=2030-01-07" : path;
                String body = mvc.perform(get(url).header("Authorization", bearer(user)))
                        .andReturn().getResponse().getContentAsString();
                assertClean(user.getRole() + " GET " + path, body);
                calls++;
            }
        }
        assertTrue(calls > 150);
    }

    @Test
    void echoingWriteEndpoints_neverReturnCredentialFields() throws Exception {
        String pid = String.valueOf(patient.getProfileId());
        Login nurse = byRole.get(Role.NURSE);
        Login doctor = byRole.get(Role.DOCTOR);

        assertClean("POST vital-signs", mvc.perform(post("/api/vital-signs").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + pid + ",\"bloodPressure\":\"120/80\",\"heartRate\":70,\"temperature\":98.6}"))
                .andReturn().getResponse().getContentAsString());
        assertClean("POST nurse/tasks", mvc.perform(post("/api/nurse/tasks").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"dueTime\":\"2030-01-01T10:00:00\",\"patientId\":" + pid + "}"))
                .andReturn().getResponse().getContentAsString());
        assertClean("POST nurse/handover", mvc.perform(post("/api/nurse/handover").header("Authorization", bearer(nurse))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"x\",\"patientId\":" + pid + "}"))
                .andReturn().getResponse().getContentAsString());
        assertClean("POST medical-records", mvc.perform(post("/api/medical-records").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + pid + ",\"diagnosis\":\"d\",\"symptoms\":\"s\",\"treatmentProvided\":\"t\"}"))
                .andReturn().getResponse().getContentAsString());
        assertClean("POST prescriptions", mvc.perform(post("/api/prescriptions").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + pid + ",\"medicationName\":\"m\",\"dosage\":\"1\",\"frequency\":\"1\",\"duration\":\"1\"}"))
                .andReturn().getResponse().getContentAsString());
        assertClean("POST lab-results", mvc.perform(post("/api/lab-results").header("Authorization", bearer(doctor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + pid + ",\"testName\":\"t\",\"testCategory\":\"c\"}"))
                .andReturn().getResponse().getContentAsString());
        assertClean("POST consent", mvc.perform(post("/api/consent").header("Authorization", bearer(byRole.get(Role.PATIENT)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantedToId\":" + doctor.getUserId() + ",\"consentType\":\"PRESCRIPTIONS\"}"))
                .andReturn().getResponse().getContentAsString());
    }
}
