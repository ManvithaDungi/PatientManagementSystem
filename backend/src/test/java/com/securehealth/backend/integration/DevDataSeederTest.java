package com.securehealth.backend.integration;

import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** CALYX-037: development demo data is created only on request, uses @example.test and a caller-supplied password. */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:devseeddb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.dev-seed.enabled=true",
        "app.dev-seed.password=Dev-Only-Seed-Passphrase-7"
})
class DevDataSeederTest {

    @Autowired LoginRepository logins;
    @Autowired PatientProfileRepository patients;
    @Autowired AuthService authService;

    @Test
    void demoAccountsExist_useExampleTestAddresses_andTheConfiguredPassword() {
        assertTrue(logins.findByEmail("admin@example.test").isPresent());
        assertTrue(logins.findByEmail("nurse1@example.test").isPresent());
        logins.findAll().stream().filter(l -> l.getEmail().endsWith("@example.test"))
                .forEach(l -> assertNotEquals("Dev-Only-Seed-Passphrase-7", l.getPasswordHash(), "stored hashed"));
        // the care team is wired, so the nurse/doctor screens work on a fresh database
        assertTrue(patients.findAll().stream().allMatch(p -> p.getAssignedDoctor() != null && p.getAssignedNurse() != null));
        var login = authService.login("patient1@example.test", "Dev-Only-Seed-Passphrase-7", "127.0.0.1", "test");
        assertEquals("LOGIN_SUCCESS", login.getStatus());
    }
}
