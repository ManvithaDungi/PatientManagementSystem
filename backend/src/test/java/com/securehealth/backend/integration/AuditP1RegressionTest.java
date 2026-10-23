package com.securehealth.backend.integration;

import com.securehealth.backend.model.*;
import com.securehealth.backend.repository.*;
import com.securehealth.backend.service.EmailService;
import com.securehealth.backend.service.TokenBlacklistService;
import com.securehealth.backend.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the P1 audit items: refresh-token theft rollback, idle-timeout bypass,
 * care-team assignment and treating-doctor access. Deliberately NOT @Transactional so that
 * commit/rollback behaviour of the service layer is exercised for real.
 */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditp1db",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class AuditP1RegressionTest {

    @Autowired MockMvc mvc;
    @Autowired LoginRepository logins;
    @Autowired PatientProfileRepository patients;
    @Autowired SessionRepository sessions;
    @Autowired AppointmentRepository appointments;
    @Autowired JwtUtil jwt;
    @Autowired TokenBlacklistService blacklist; // Mockito mock from TestConfig
    @MockBean EmailService emailService;

    String sfx;

    @BeforeEach
    void init() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        Mockito.reset(blacklist);
    }

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

    static String hash(String token) throws Exception {
        return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    Session session(Login u, String rawToken, boolean revoked) throws Exception {
        Session s = new Session();
        s.setUser(u);
        s.setRefreshTokenHash(hash(rawToken));
        s.setExpiresAt(LocalDateTime.now().plusDays(1));
        s.setRevoked(revoked);
        return sessions.saveAndFlush(s);
    }

    PatientProfile patientFor(Login pu) {
        PatientProfile p = new PatientProfile();
        p.setUser(pu);
        p.setFirstName("Pat");
        p.setLastName("Ient");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return patients.saveAndFlush(p);
    }

    @Test
    void reusedRefreshToken_revokesAllSessionsAndPersistsIt() throws Exception {
        Login user = mk("victim", Role.PATIENT);
        session(user, "stolen-old-token", true);
        Session live = session(user, "legit-current-token", false);

        mvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refreshToken", "stolen-old-token")))
                .andExpect(status().isUnauthorized());

        assertTrue(sessions.findById(live.getId()).orElseThrow().isRevoked(),
                "theft detection must persist the revocation of every session");
    }

    @Test
    void refresh_isRefusedWhenSessionIsIdle() throws Exception {
        Login user = mk("idle", Role.PATIENT);
        Session s = session(user, "idle-token", false);
        Mockito.when(blacklist.isSessionIdle("s:" + s.getId())).thenReturn(true);

        mvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refreshToken", "idle-token")))
                .andExpect(status().isUnauthorized());

        assertTrue(sessions.findById(s.getId()).orElseThrow().isRevoked());
    }

    @Test
    void refresh_succeedsForActiveSession() throws Exception {
        Login user = mk("active", Role.PATIENT);
        session(user, "active-token", false);
        Mockito.when(blacklist.isSessionIdle(user.getEmail())).thenReturn(false);

        mvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refreshToken", "active-token")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanAssignCareTeam_andNurseThenSeesPatient() throws Exception {
        Login admin = mk("admin", Role.ADMIN);
        Login nurse = mk("nurse", Role.NURSE);
        Login doctor = mk("doc", Role.DOCTOR);
        PatientProfile p = patientFor(mk("pat", Role.PATIENT));

        String req = "{\"assignedDoctorId\":" + doctor.getUserId() + ",\"assignedNurseId\":" + nurse.getUserId() + "}";
        mvc.perform(put("/api/admin/patients/" + p.getProfileId() + "/care-team")
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/api/nurse/assigned-patients").header("Authorization", bearer(nurse)))
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"profileId\":" + p.getProfileId()), body);
    }

    @Test
    void careTeam_rejectsWrongRoleAndNonAdmin() throws Exception {
        Login admin = mk("admin", Role.ADMIN);
        Login nurse = mk("nurse", Role.NURSE);
        PatientProfile p = patientFor(mk("pat", Role.PATIENT));

        // a nurse id supplied as the doctor
        String bad = "{\"assignedDoctorId\":" + nurse.getUserId() + "}";
        mvc.perform(put("/api/admin/patients/" + p.getProfileId() + "/care-team")
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/admin/patients/" + p.getProfileId() + "/care-team")
                .header("Authorization", bearer(nurse)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorWithAppointment_canOpenPatient_butUnrelatedDoctorCannot() throws Exception {
        Login doctor = mk("treating", Role.DOCTOR);
        Login stranger = mk("stranger", Role.DOCTOR);
        PatientProfile p = patientFor(mk("pat", Role.PATIENT));
        Appointment a = new Appointment();
        a.setPatient(p);
        a.setDoctor(doctor);
        a.setAppointmentDate(LocalDateTime.now().plusDays(3));
        a.setStatus(AppointmentStatus.SCHEDULED);
        appointments.saveAndFlush(a);

        mvc.perform(get("/api/patients/" + p.getProfileId()).header("Authorization", bearer(doctor)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/patients/" + p.getProfileId()).header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
    }
}
