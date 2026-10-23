package com.securehealth.backend.integration;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The admin "security events", "user activity" and "system health" endpoints are
 * derived from real data (audit log, live dependency checks), not static values.
 */
@SpringBootTest(classes = {
        com.securehealth.backend.SecureHealthApplication.class,
        TestConfig.class
})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admininsightstestdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class AdminInsightsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LoginRepository loginRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtUtil jwtUtil;

    private String adminToken;
    private String doctorToken;

    @BeforeEach
    void setUp() {
        adminToken = bearer(newLogin("insights.admin@hospital.test", Role.ADMIN));
        doctorToken = bearer(newLogin("insights.doc@hospital.test", Role.DOCTOR));
        auditLogRepository.deleteAll();

        audit("a@x.test", "LOGIN_SUCCESS");
        audit("a@x.test", "LOGIN_SUCCESS");
        audit("b@x.test", "LOGIN_FAILED");
        audit("b@x.test", "ACCOUNT_LOCKED");
        audit("c@x.test", "API_ACTIVITY");
    }

    private void audit(String email, String action) {
        auditLogRepository.save(new AuditLog(email, action, "10.0.0.1", "JUnit", "seeded"));
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

    @Test
    void securityEvents_returnsOnlySecurityRelevantAuditEntries() throws Exception {
        mockMvc.perform(get("/api/admin/security-events").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.action=='LOGIN_FAILED')]").exists())
                .andExpect(jsonPath("$[?(@.action=='ACCOUNT_LOCKED')]").exists())
                .andExpect(jsonPath("$[?(@.action=='LOGIN_SUCCESS')]").doesNotExist());
    }

    @Test
    void userActivity_countsAuditEventsByAction() throws Exception {
        mockMvc.perform(get("/api/admin/user-activity").param("timeframe", "7d")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(5))
                .andExpect(jsonPath("$.securityEvents").value(2))
                .andExpect(jsonPath("$.successfulLogins").value(2))
                .andExpect(jsonPath("$.failedLogins").value(1))
                .andExpect(jsonPath("$.byAction.API_ACTIVITY").value(1));
    }

    @Test
    void systemHealth_reportsLiveDatabaseStatus() throws Exception {
        mockMvc.perform(get("/api/admin/system-health").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.redis.status").exists())
                .andExpect(jsonPath("$.memoryUsedPercent").isNumber())
                .andExpect(jsonPath("$.uptimeSeconds").isNumber());
    }

    @Test
    void insightEndpoints_areAdminOnly() throws Exception {
        for (String path : new String[] {"/api/admin/security-events", "/api/admin/user-activity",
                "/api/admin/system-health"}) {
            mockMvc.perform(get(path).header("Authorization", doctorToken)).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
