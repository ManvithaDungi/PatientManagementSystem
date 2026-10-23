package com.securehealth.backend.service;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.LoginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the very first administrator on an empty system so staff invites
 * have someone to issue them.
 * <p>
 * Runs only when <b>no ADMIN exists</b> and both {@code APP_ADMIN_EMAIL} and
 * {@code APP_ADMIN_PASSWORD} are supplied. There is deliberately no default
 * password: if the variables are absent nothing is created. Remove
 * {@code APP_ADMIN_PASSWORD} from the environment after the first start.
 * </p>
 */
@Component
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    @Autowired private LoginRepository loginRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (loginRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        // Same strength rules as every other password in the system.
        authService.validatePasswordStrength(adminPassword);

        Login admin = new Login();
        admin.setEmail(adminEmail.trim().toLowerCase(java.util.Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setTwoFactorEnabled(true);
        loginRepository.save(admin);

        log.info("Bootstrap administrator created for {}. Remove APP_ADMIN_PASSWORD from the environment.",
                adminEmail.trim());
    }
}
