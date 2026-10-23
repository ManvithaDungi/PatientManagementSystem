package com.securehealth.backend.integration;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A patient with no consent yet must be able to grant a first one, and only for
 * data categories and recipients the access checks actually understand.
 */
@SpringBootTest(classes = {
        com.securehealth.backend.SecureHealthApplication.class,
        TestConfig.class
})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:consentgranttestdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class ConsentGrantIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private JwtUtil jwtUtil;

    private Login patient;
    private Login doctor;
    private Login admin;

    @BeforeEach
    void setUp() {
        patient = newLogin("consent.patient@hospital.test", Role.PATIENT);
        doctor = newLogin("consent.doc@hospital.test", Role.DOCTOR);
        admin = newLogin("consent.admin@hospital.test", Role.ADMIN);

        PatientProfile profile = new PatientProfile();
        profile.setUser(patient);
        profile.setFirstName("Con");
        profile.setLastName("Sent");
        profile.setDateOfBirth(LocalDate.of(1991, 2, 3));
        patientProfileRepository.save(profile);
    }

    private Login newLogin(String email, Role role) {
        Login login = new Login();
        login.setEmail(email);
        login.setPasswordHash("not-a-real-hash");
        login.setRole(role);
        return loginRepository.save(login);
    }

    private String bearer(Login user) {
        return "Bearer " + jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getUserId());
    }

    private String body(Long providerId, String type) {
        return "{\"grantedToId\":" + providerId + ",\"consentType\":\"" + type + "\",\"reason\":\"first grant\"}";
    }

    @Test
    void patientWithNoConsents_canGrantFirstConsent() throws Exception {
        mockMvc.perform(get("/api/consent").header("Authorization", bearer(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body(doctor.getUserId(), "LAB_RESULTS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentType").value("LAB_RESULTS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/consent").header("Authorization", bearer(patient)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unknownConsentType_isRejected() throws Exception {
        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body(doctor.getUserId(), "VIEW_RECORDS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consentCannotBeGrantedToNonClinicalAccounts() throws Exception {
        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body(admin.getUserId(), "ALL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingProvider_isRejected() throws Exception {
        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"consentType\":\"ALL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateActiveGrant_isRejected() throws Exception {
        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body(doctor.getUserId(), "PRESCRIPTIONS")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body(doctor.getUserId(), "PRESCRIPTIONS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Active consent already exists for this provider and data type."));
    }

    @Test
    void utcExpiryFromBrowser_isAcceptedButPastExpiryIsNot() throws Exception {
        String future = java.time.Instant.now().plus(java.time.Duration.ofDays(30)).toString();
        String past = java.time.Instant.now().minus(java.time.Duration.ofDays(1)).toString();

        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantedToId\":" + doctor.getUserId()
                                + ",\"consentType\":\"VITAL_SIGNS\",\"expiresAt\":\"" + future + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(post("/api/consent").header("Authorization", bearer(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantedToId\":" + doctor.getUserId()
                                + ",\"consentType\":\"MEDICAL_RECORDS\",\"expiresAt\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
