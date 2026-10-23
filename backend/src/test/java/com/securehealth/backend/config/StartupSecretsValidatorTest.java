package com.securehealth.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class StartupSecretsValidatorTest {

    private static final String GOOD_JWT = Base64.getEncoder().encodeToString(new byte[48]);
    private static final String GOOD_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private StartupSecretsValidator validator(String jwt, String key, String dbPassword, boolean enforce) {
        StartupSecretsValidator v = new StartupSecretsValidator();
        ReflectionTestUtils.setField(v, "jwtSecret", jwt);
        ReflectionTestUtils.setField(v, "encryptionKey", key);
        ReflectionTestUtils.setField(v, "datasourcePassword", dbPassword);
        ReflectionTestUtils.setField(v, "enforce", enforce);
        return v;
    }

    @Test
    void acceptsStrongSecrets() {
        assertDoesNotThrow(() -> validator(GOOD_JWT, GOOD_KEY, "a-unique-db-password", true).validate());
    }

    @Test
    void rejectsMissingSecrets() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> validator("", "", "", true).validate());
        assertTrue(e.getMessage().contains("jwt.secret"));
        assertTrue(e.getMessage().contains("app.encryption.key"));
        assertTrue(e.getMessage().contains("spring.datasource.password"));
    }

    @Test
    void rejectsSecretsThatWerePublishedInSourceControl() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validator(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                "dGhpcyBpcyBhIDMyIGJ5dGUga2V5IGZvciB0ZXN0aW5nIQ==",
                "temp_pass", true).validate());
        assertTrue(e.getMessage().contains("published in source control"));
    }

    @Test
    void rejectsWrongSizedKeys() {
        String shortJwt = Base64.getEncoder().encodeToString(new byte[16]);
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> validator(shortJwt, shortKey, "x-unique-password", true).validate());
        assertTrue(e.getMessage().contains("at least 32 bytes"));
        assertTrue(e.getMessage().contains("exactly 32 bytes"));
    }

    @Test
    void errorMessageNeverEchoesTheSecretValue() {
        String secret = "dGhpcyBpcyBhIDMyIGJ5dGUga2V5IGZvciB0ZXN0aW5nIQ==";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> validator(GOOD_JWT, secret, "x-unique-password", true).validate());
        assertFalse(e.getMessage().contains(secret));
    }

    @Test
    void canBeDisabledForTests() {
        assertDoesNotThrow(() -> validator("", "", "", false).validate());
    }
}
