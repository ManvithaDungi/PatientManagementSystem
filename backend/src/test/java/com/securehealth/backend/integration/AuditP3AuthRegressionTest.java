package com.securehealth.backend.integration;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.service.AuthService;
import com.securehealth.backend.service.EmailService;
import com.securehealth.backend.service.TokenBlacklistService;
import com.securehealth.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the authentication items: CALYX-025, 026, 027, 033, 036, 040. */
@SpringBootTest(classes = {com.securehealth.backend.SecureHealthApplication.class, TestConfig.class})
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditp3authdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "jwt.expiration=900000"
})
class AuditP3AuthRegressionTest {

    private static final String PW = "Str0ng-Unusual-Phrase-91";

    @Autowired MockMvc mvc;
    @Autowired LoginRepository logins;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwt;
    @Autowired TokenBlacklistService blacklist; // Mockito mock from TestConfig
    @MockBean EmailService emailService;

    String sfx;

    @BeforeEach
    void init() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        Mockito.reset(blacklist);
    }

    Login mk(String name, Role r, boolean twoFactor) {
        Login l = new Login();
        l.setEmail(name + "-" + sfx + "@t.test");
        l.setPasswordHash(encoder.encode(PW));
        l.setRole(r);
        l.setTwoFactorEnabled(twoFactor);
        return logins.saveAndFlush(l);
    }

    String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getContentAsString();
    }

    String field(String json, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------ CALYX-025 one-time passwords
    @Test
    void otp_isSixRandomDigits_storedHashed_andBoundToAPendingToken() throws Exception {
        Login doctor = mk("doc", Role.DOCTOR, true);
        String first = login(doctor.getEmail(), PW);
        assertEquals("OTP_REQUIRED", field(first, "status"), first);
        String pending = field(first, "pendingToken");
        assertNotNull(pending);
        assertNull(field(first, "accessToken"));

        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailService).sendOtp(eq(doctor.getEmail()), otp.capture());
        assertTrue(otp.getValue().matches("\\d{6}"), otp.getValue());

        Login stored = logins.findById(doctor.getUserId()).orElseThrow();
        assertNotEquals(otp.getValue(), stored.getOtp(), "the OTP must not be stored in clear text");
        assertEquals(AuthService.hashOtp(otp.getValue(), doctor.getUserId()), stored.getOtp());

        String email = doctor.getEmail();
        // correct OTP without the pending token, or with a wrong one, is refused
        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp.getValue() + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp.getValue() + "\",\"pendingToken\":\"wrong\"}")).andExpect(status().isUnauthorized());
        // wrong OTP with the right token is refused
        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"otp\":\"000000\",\"pendingToken\":\"" + pending + "\"}")).andExpect(status().isUnauthorized());
        // both correct
        MvcResult ok = mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp.getValue() + "\",\"pendingToken\":\"" + pending + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertNotNull(field(ok.getResponse().getContentAsString(), "accessToken"));
        Login after = logins.findById(doctor.getUserId()).orElseThrow();
        assertNull(after.getOtp());
        assertNull(after.getOtpPendingTokenHash());
        // the OTP cannot be replayed
        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp.getValue() + "\",\"pendingToken\":\"" + pending + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void resendOtp_requiresThePendingToken_andInvalidatesTheOldCode() throws Exception {
        Login doctor = mk("doc2", Role.DOCTOR, true);
        String first = login(doctor.getEmail(), PW);
        String pending = field(first, "pendingToken");
        ArgumentCaptor<String> otps = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailService).sendOtp(eq(doctor.getEmail()), otps.capture());
        String oldOtp = otps.getValue();

        // an anonymous caller who only knows the address can no longer reset someone's OTP
        mvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + doctor.getEmail() + "\"}")).andExpect(status().isBadRequest());
        Mockito.verify(emailService, Mockito.times(1)).sendOtp(anyString(), anyString());

        mvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + doctor.getEmail() + "\",\"pendingToken\":\"" + pending + "\"}")).andExpect(status().isOk());
        ArgumentCaptor<String> again = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailService, Mockito.times(2)).sendOtp(eq(doctor.getEmail()), again.capture());
        String newOtp = again.getAllValues().get(1);

        if (!newOtp.equals(oldOtp)) {
            mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + doctor.getEmail() + "\",\"otp\":\"" + oldOtp + "\",\"pendingToken\":\"" + pending + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + doctor.getEmail() + "\",\"otp\":\"" + newOtp + "\",\"pendingToken\":\"" + pending + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void enableTwoFactor_nowReallyAppliesToEveryRole() throws Exception {
        Login patient = mk("pat2fa", Role.PATIENT, false);
        String token = "Bearer " + jwt.generateAccessToken(patient.getEmail(), "PATIENT", patient.getUserId());
        mvc.perform(post("/api/auth/enable-2fa").header("Authorization", token)).andExpect(status().isOk());
        String reply = login(patient.getEmail(), PW);
        assertEquals("OTP_REQUIRED", field(reply, "status"), reply);
    }

    // ------------------------------------------------------------ CALYX-026 case-insensitive e-mail, uniform errors
    @Test
    void email_isCaseInsensitive_andRegistrationDoesNotRevealExistingAccounts() throws Exception {
        String mixed = "MiXeD-" + sfx + "@Case.test";
        String body = "{\"email\":\"" + mixed + "\",\"password\":\"" + PW + "\",\"full_name\":\"Case Tester\",\"date_of_birth\":\"1990-01-01\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertTrue(logins.findByEmail(mixed.toLowerCase()).isPresent(), "addresses are stored lower-case");
        long before = logins.count();

        // same address in another case: identical answer, no second account
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body.replace(mixed, mixed.toUpperCase()))).andExpect(status().isCreated());
        assertEquals(before, logins.count());

        // sign-in works whatever case is typed
        assertNotNull(field(login(mixed.toUpperCase(), PW), "accessToken"));
    }

    @Test
    void loginFailures_lookIdentical_forUnknownLockedDisabledAndWrongPassword() throws Exception {
        Login known = mk("known", Role.PATIENT, false);
        Login locked = mk("locked", Role.PATIENT, false);
        locked.setLocked(true);
        logins.saveAndFlush(locked);
        Login disabled = mk("disabled", Role.PATIENT, false);
        disabled.setArchived(true);
        logins.saveAndFlush(disabled);

        String wrong = login(known.getEmail(), "not-the-password");
        String unknown = login("nobody-" + sfx + "@t.test", PW);
        String lockedReply = login(locked.getEmail(), PW);
        String disabledReply = login(disabled.getEmail(), PW);
        for (String reply : new String[]{unknown, lockedReply, disabledReply}) {
            assertEquals(field(wrong, "message"), field(reply, "message"), reply);
        }
        assertEquals("Invalid credentials", field(wrong, "message"));
    }

    // ------------------------------------------------------------ CALYX-040 Argon2 strength with rehash on login
    @Test
    void argon2_usesOwaspMinimumParameters_andOldHashesAreUpgradedAtLogin() throws Exception {
        assertTrue(encoder.encode("x").contains("m=19456,t=2,p=1"), "new hashes use the OWASP minimum parameters");

        Login legacy = mk("legacy", Role.PATIENT, false);
        String oldHash = new Argon2PasswordEncoder(16, 32, 1, 4096, 3).encode(PW);
        assertTrue(oldHash.contains("m=4096,t=3,p=1"));
        legacy.setPasswordHash(oldHash);
        logins.saveAndFlush(legacy);

        assertNotNull(field(login(legacy.getEmail(), PW), "accessToken"), "an old hash must still verify");
        String upgraded = logins.findById(legacy.getUserId()).orElseThrow().getPasswordHash();
        assertTrue(upgraded.contains("m=19456,t=2,p=1"), upgraded);
        assertNotNull(field(login(legacy.getEmail(), PW), "accessToken"), "and still verify after the upgrade");
    }

    // ------------------------------------------------------------ CALYX-033 / 036 identity and session binding
    @Test
    void authMe_reportsTheServerSideRole() throws Exception {
        Login nurse = mk("me", Role.NURSE, false);
        String token = "Bearer " + jwt.generateAccessToken(nurse.getEmail(), "ADMIN" /* forged claim */, nurse.getUserId());
        String me = mvc.perform(get("/api/auth/me").header("Authorization", token)).andReturn().getResponse().getContentAsString();
        assertEquals("NURSE", field(me, "role"), "the role comes from the database, not from the token claim");
    }

    @Test
    void accessToken_isBoundToItsSession_andIdleIsTrackedPerSession() throws Exception {
        Login user = mk("sid", Role.PATIENT, false);
        String reply = login(user.getEmail(), PW);
        String access = field(reply, "accessToken");
        String sid = jwt.extractSessionId(access);
        assertNotNull(sid, "the access token carries its session id");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        Mockito.verify(blacklist, Mockito.atLeastOnce()).updateLastActive("s:" + sid);

        // idle in THIS session only
        Mockito.when(blacklist.isSessionIdle("s:" + sid)).thenReturn(true);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void blacklistKeys_neverContainTheRawToken() {
        StringRedisTemplateProbe probe = new StringRedisTemplateProbe();
        probe.blacklistAndCapture("header.payload.signature-" + sfx);
        assertFalse(probe.capturedKey.contains("signature-" + sfx), probe.capturedKey);
        assertTrue(probe.capturedKey.startsWith("jwt:blacklist:"));
        assertEquals("jwt:blacklist:".length() + 64, probe.capturedKey.length(), "a hex SHA-256 digest");
    }

    /** Minimal harness around the real TokenBlacklistService with a mocked Redis template. */
    static class StringRedisTemplateProbe {
        String capturedKey;

        @SuppressWarnings("unchecked")
        void blacklistAndCapture(String token) {
            org.springframework.data.redis.core.StringRedisTemplate redis = Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
            org.springframework.data.redis.core.ValueOperations<String, String> ops = Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
            Mockito.when(redis.opsForValue()).thenReturn(ops);
            TokenBlacklistService real = new TokenBlacklistService();
            org.springframework.test.util.ReflectionTestUtils.setField(real, "redisTemplate", redis);
            real.blacklistToken(token, 60_000);
            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            Mockito.verify(ops).set(key.capture(), anyString(), Mockito.any(java.time.Duration.class));
            capturedKey = key.getValue();
        }
    }
}
