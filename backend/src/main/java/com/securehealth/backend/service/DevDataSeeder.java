package com.securehealth.backend.service;

import com.securehealth.backend.model.DoctorProfile;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.DoctorProfileRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Creates demo accounts for local development. Active ONLY with {@code SPRING_PROFILES_ACTIVE=dev} and
 * {@code APP_DEV_SEED_ENABLED=true}. No password is stored in the repository: it comes from
 * {@code APP_DEV_SEED_PASSWORD}, or a random one is generated and logged once.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789-_";

    @Autowired private LoginRepository loginRepository;
    @Autowired private DoctorProfileRepository doctorProfileRepository;
    @Autowired private PatientProfileRepository patientProfileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;

    @Value("${app.dev-seed.password:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (loginRepository.findByEmail("admin@example.test").isPresent()) {
            log.info("Demo accounts already exist; nothing to seed.");
            return;
        }
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? randomPassword() : configuredPassword;
        if (!generated) {
            authService.validatePasswordStrength(password);
        }
        String hash = passwordEncoder.encode(password);

        Login admin = account("admin@example.test", Role.ADMIN, hash, true);
        Login doctor1 = account("doctor1@example.test", Role.DOCTOR, hash, true);
        Login doctor2 = account("doctor2@example.test", Role.DOCTOR, hash, true);
        Login nurse = account("nurse1@example.test", Role.NURSE, hash, false);
        account("lab1@example.test", Role.LAB_TECHNICIAN, hash, false);
        Login patient1 = account("patient1@example.test", Role.PATIENT, hash, false);
        Login patient2 = account("patient2@example.test", Role.PATIENT, hash, false);

        doctor(doctor1, "Alex", "Morgan", "Cardiology");
        doctor(doctor2, "Sam", "Rivera", "General Practice");
        patient(patient1, "Pat", "Example", LocalDate.of(1985, 4, 12), doctor1, nurse);
        patient(patient2, "Chris", "Sample", LocalDate.of(1992, 9, 30), doctor2, nurse);

        log.warn("Development demo accounts created for {} (admin, doctor1, doctor2, nurse1, lab1, patient1, patient2 @example.test).",
                admin.getEmail().substring(admin.getEmail().indexOf('@')));
        if (generated) {
            log.warn("Generated demo password (shown once, set APP_DEV_SEED_PASSWORD to choose your own): {}", password);
        }
    }

    private Login account(String email, Role role, String hash, boolean twoFactor) {
        Login l = new Login();
        l.setEmail(email);
        l.setPasswordHash(hash);
        l.setRole(role);
        l.setTwoFactorEnabled(twoFactor);
        l.setLastLoginAt(java.time.LocalDateTime.now());
        return loginRepository.save(l);
    }

    private void doctor(Login user, String first, String last, String specialty) {
        DoctorProfile p = new DoctorProfile();
        p.setUser(user);
        p.setFirstName(first);
        p.setLastName(last);
        p.setSpecialty(specialty);
        p.setDepartment(specialty);
        p.setWorkingDays(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        doctorProfileRepository.save(p);
    }

    private void patient(Login user, String first, String last, LocalDate dob, Login doctor, Login nurse) {
        PatientProfile p = new PatientProfile();
        p.setUser(user);
        p.setFirstName(first);
        p.setLastName(last);
        p.setDateOfBirth(dob);
        p.setAssignedDoctor(doctor);
        p.setAssignedNurse(nurse);
        patientProfileRepository.save(p);
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
