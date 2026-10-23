package com.securehealth.backend.integration;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.repository.StaffInviteRepository;
import com.securehealth.backend.service.EmailService;
import com.securehealth.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression tests for the authorization gaps closed in the "SecureHealth gaps"
 * remediation: public role-from-body registration, unauthenticated enable-2fa,
 * the unguarded appointments-by-status endpoint, the assigned-doctor consent
 * exemption, and staff onboarding via invite links.
 */
@SpringBootTest(classes = {
        com.securehealth.backend.SecureHealthApplication.class,
        TestConfig.class
})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitygapstestdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class SecurityGapsIntegrationTest {

    private static final String STRONG_PASSWORD = "Str0ng-Unusual-Phrase-91";

    @Autowired private MockMvc mockMvc;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private StaffInviteRepository inviteRepository;
    @Autowired private JwtUtil jwtUtil;

    @MockBean private EmailService emailService;

    private Login admin;
    private Login assignedDoctor;
    private Login otherDoctor;
    private Login patientUser;
    private PatientProfile patientProfile;

    @BeforeEach
    void setUp() {
        admin = newLogin("admin@hospital.test", Role.ADMIN);
        assignedDoctor = newLogin("assigned.doc@hospital.test", Role.DOCTOR);
        otherDoctor = newLogin("other.doc@hospital.test", Role.DOCTOR);
        patientUser = newLogin("patient@hospital.test", Role.PATIENT);

        patientProfile = new PatientProfile();
        patientProfile.setUser(patientUser);
        patientProfile.setFirstName("Pat");
        patientProfile.setLastName("Ient");
        patientProfile.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patientProfile.setAssignedDoctor(assignedDoctor);
        patientProfile = patientProfileRepository.save(patientProfile);
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

    // ------------------------------------------------------------------ #1

    @Test
    void publicRegistration_ignoresRoleSuppliedInBody() throws Exception {
        String body = "{\"email\":\"sneaky@evil.test\",\"password\":\"" + STRONG_PASSWORD + "\",\"role\":\"ADMIN\",\"full_name\":\"Sneaky Person\",\"date_of_birth\":\"1990-01-01\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        assertEquals(Role.PATIENT, loginRepository.findByEmail("sneaky@evil.test").orElseThrow().getRole());
    }

    // ------------------------------------------------------------------ #2

    @Test
    void enableTwoFactor_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/enable-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"patient@hospital.test\"}"))
                .andExpect(status().isUnauthorized());

        assertFalse(loginRepository.findByEmail("patient@hospital.test").orElseThrow().isTwoFactorEnabled());
    }

    @Test
    void enableTwoFactor_actsOnTokenIdentityNotBodyEmail() throws Exception {
        mockMvc.perform(post("/api/auth/enable-2fa")
                        .header("Authorization", bearer(patientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other.doc@hospital.test\"}"))
                .andExpect(status().isOk());

        assertTrue(loginRepository.findByEmail("patient@hospital.test").orElseThrow().isTwoFactorEnabled());
        assertFalse(loginRepository.findByEmail("other.doc@hospital.test").orElseThrow().isTwoFactorEnabled());
    }

    @Test
    void currentUserEndpoint_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ #3

    @Test
    void appointmentsByStatus_isGuardedByRole() throws Exception {
        mockMvc.perform(get("/api/appointments/status/SCHEDULED"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/appointments/status/SCHEDULED")
                        .header("Authorization", bearer(patientUser)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/appointments/status/SCHEDULED")
                        .header("Authorization", bearer(assignedDoctor)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/appointments/status/NOT_A_STATUS")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------- #10

    @Test
    void assignedDoctor_readsClinicalDataWithoutConsent() throws Exception {
        mockMvc.perform(get("/api/prescriptions/patient/" + patientProfile.getProfileId())
                        .header("Authorization", bearer(assignedDoctor)))
                .andExpect(status().isOk());
    }

    @Test
    void unassignedDoctor_withoutConsent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/prescriptions/patient/" + patientProfile.getProfileId())
                        .header("Authorization", bearer(otherDoctor)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ #1

    @Test
    void invite_cannotBeCreatedByNonAdmins() throws Exception {
        mockMvc.perform(post("/api/admin/staff/invites")
                        .header("Authorization", bearer(patientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.nurse@hospital.test\",\"role\":\"NURSE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/staff/invites")
                        .header("Authorization", bearer(assignedDoctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.nurse@hospital.test\",\"role\":\"NURSE\"}"))
                .andExpect(status().isForbidden());

        verify(emailService, never()).sendStaffInvite(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void invite_rejectsPatientRoleAndExistingAccounts() throws Exception {
        mockMvc.perform(post("/api/admin/staff/invites")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@hospital.test\",\"role\":\"PATIENT\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/staff/invites")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other.doc@hospital.test\",\"role\":\"DOCTOR\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invite_fullLifecycle_createsStaffWithInvitedRoleOnly_andIsSingleUse() throws Exception {
        String token = issueInvite("new.nurse@hospital.test", "NURSE");

        mockMvc.perform(get("/api/auth/invite/validate").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.nurse@hospital.test"))
                .andExpect(jsonPath("$.role").value("NURSE"));

        // A role in the accept body must be ignored — the invite decides.
        acceptInvite("{\"token\":\"" + token + "\",\"password\":\"" + STRONG_PASSWORD
                + "\",\"fullName\":\"Nora Nurse\",\"role\":\"ADMIN\"}")
                .andExpect(status().isCreated());

        Login created = loginRepository.findByEmail("new.nurse@hospital.test").orElseThrow();
        assertEquals(Role.NURSE, created.getRole());

        // Single use.
        acceptInvite("{\"token\":\"" + token + "\",\"password\":\"" + STRONG_PASSWORD
                + "\",\"fullName\":\"Nora Nurse\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void invite_doctorRequiresSpecialty_andGetsTwoFactor() throws Exception {
        String token = issueInvite("new.doc@hospital.test", "DOCTOR");

        acceptInvite("{\"token\":\"" + token + "\",\"password\":\"" + STRONG_PASSWORD
                + "\",\"fullName\":\"Dan Doctor\"}")
                .andExpect(status().isBadRequest());
        assertTrue(loginRepository.findByEmail("new.doc@hospital.test").isEmpty());

        acceptInvite("{\"token\":\"" + token + "\",\"password\":\"" + STRONG_PASSWORD
                + "\",\"fullName\":\"Dan Doctor\",\"specialty\":\"Cardiology\"}")
                .andExpect(status().isCreated());

        Login created = loginRepository.findByEmail("new.doc@hospital.test").orElseThrow();
        assertEquals(Role.DOCTOR, created.getRole());
        assertTrue(created.isTwoFactorEnabled());
    }

    @Test
    void invite_unknownRevokedAndSupersededTokensAreRejected() throws Exception {
        acceptInvite("{\"token\":\"not-a-real-token\",\"password\":\"" + STRONG_PASSWORD
                + "\",\"fullName\":\"X\"}")
                .andExpect(status().isBadRequest());

        // Re-inviting the same email invalidates the earlier link.
        String first = issueInvite("lab@hospital.test", "LAB_TECHNICIAN");
        String second = issueInvite("lab@hospital.test", "LAB_TECHNICIAN");
        assertNotEquals(first, second);
        mockMvc.perform(get("/api/auth/invite/validate").param("token", first))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/invite/validate").param("token", second))
                .andExpect(status().isOk());

        // Explicit revoke.
        Long inviteId = inviteRepository.findAll().stream()
                .filter(i -> !i.isRevoked() && !i.isUsed())
                .findFirst().orElseThrow().getId();
        mockMvc.perform(delete("/api/admin/staff/invites/" + inviteId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/invite/validate").param("token", second))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invite_expiredTokenIsRejected() throws Exception {
        String token = issueInvite("late@hospital.test", "NURSE");
        inviteRepository.findAll().forEach(i -> {
            i.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
            inviteRepository.save(i);
        });

        mockMvc.perform(get("/api/auth/invite/validate").param("token", token))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ #8

    @Autowired private com.securehealth.backend.repository.AuditLogRepository auditLogRepository;

    @Test
    void auditLogs_arePaginatedFilteredAndAdminOnly() throws Exception {
        for (int i = 0; i < 7; i++) {
            auditLogRepository.save(new com.securehealth.backend.model.AuditLog(
                    i < 5 ? "alice@hospital.test" : "bob@hospital.test", "ACTION_" + i, "127.0.0.1", "ua", "d"));
        }

        mockMvc.perform(get("/api/admin/audit-logs").param("size", "3").param("email", "alice@hospital.test")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(3));

        // An oversized page request is clamped, never honoured.
        mockMvc.perform(get("/api/admin/audit-logs").param("size", "100000")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(patientUser)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private String issueInvite(String email, String role) throws Exception {
        mockMvc.perform(post("/api/admin/staff/invites")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
                .sendStaffInvite(eq(email), eq(role), link.capture(), anyInt());
        String lastLink = link.getAllValues().get(link.getAllValues().size() - 1);
        return lastLink.substring(lastLink.indexOf("token=") + "token=".length());
    }

    private ResultActions acceptInvite(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/invite/accept")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
