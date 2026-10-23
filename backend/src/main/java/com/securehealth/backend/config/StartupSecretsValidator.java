package com.securehealth.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Refuses to start the application with missing, weak or publicly known
 * secrets.
 * <p>
 * The values listed in {@link #KNOWN_COMPROMISED} were committed to a public
 * repository at some point and must be treated as burned. Enforcement can only
 * be switched off with {@code app.secrets.enforce=false}, which the test
 * profile does; the production default is {@code true}.
 * </p>
 * Failure messages name the offending <em>property</em>, never its value.
 */
@Component
public class StartupSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupSecretsValidator.class);

    private static final Set<String> KNOWN_COMPROMISED = Set.of(
            // previously committed jwt.secret
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            // previously committed app.encryption.key default
            "dGhpcyBpcyBhIDMyIGJ5dGUga2V5IGZvciB0ZXN0aW5nIQ==",
            // previously committed datasource / admin / sample passwords
            "temp_pass", "password", "secureBen10", "Admin@12345678"
    );

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${app.secrets.enforce:true}")
    private boolean enforce;

    @PostConstruct
    void validate() {
        if (!enforce) {
            log.warn("Secret validation is DISABLED (app.secrets.enforce=false). Never run like this in production.");
            return;
        }

        List<String> problems = new ArrayList<>();

        // JWT signing key: Base64, at least 256 bits.
        if (jwtSecret.isBlank()) {
            problems.add("jwt.secret (JWT_SECRET) is not set");
        } else if (KNOWN_COMPROMISED.contains(jwtSecret)) {
            problems.add("jwt.secret (JWT_SECRET) is a value that was published in source control; generate a new one");
        } else {
            int bytes = decodedLength(jwtSecret);
            if (bytes < 32) {
                problems.add("jwt.secret (JWT_SECRET) must be Base64 and decode to at least 32 bytes");
            }
        }

        // AES-256 file-encryption key: Base64, exactly 32 bytes.
        if (encryptionKey.isBlank()) {
            problems.add("app.encryption.key (APP_ENCRYPTION_KEY) is not set");
        } else if (KNOWN_COMPROMISED.contains(encryptionKey)) {
            problems.add("app.encryption.key (APP_ENCRYPTION_KEY) is a value that was published in source control; generate a new one");
        } else if (decodedLength(encryptionKey) != 32) {
            problems.add("app.encryption.key (APP_ENCRYPTION_KEY) must be Base64 and decode to exactly 32 bytes");
        }

        if (datasourcePassword.isBlank()) {
            problems.add("spring.datasource.password (SPRING_DATASOURCE_PASSWORD) is not set");
        } else if (KNOWN_COMPROMISED.contains(datasourcePassword)) {
            problems.add("spring.datasource.password (SPRING_DATASOURCE_PASSWORD) is a known default; choose a unique password");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start: insecure secret configuration.\n - " + String.join("\n - ", problems)
                            + "\nGenerate values with: openssl rand -base64 32");
        }
    }

    private static int decodedLength(String base64) {
        try {
            return Base64.getDecoder().decode(base64).length;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }
}
