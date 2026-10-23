package com.securehealth.backend.service;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PasswordHistory;
import com.securehealth.backend.model.PasswordResetToken;
import com.securehealth.backend.model.Role;
import com.securehealth.backend.model.Session;
import com.securehealth.backend.repository.AuditLogRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.PasswordHistoryRepository;
import com.securehealth.backend.repository.PasswordResetTokenRepository;
import com.securehealth.backend.repository.SessionRepository;
import com.securehealth.backend.repository.PatientProfileRepository;
import com.securehealth.backend.dto.LoginResponse;
import com.securehealth.backend.dto.RegistrationRequest;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.util.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Service Layer for Identity Management.
 * <p>
 * Handles the core security operations:
 * 1. Registering new users (Hashing passwords)
 * 2. Authenticating users (Verifying passwords)
 * </p>
 *
 * @author Manas
 */

@Service
public class AuthService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    /**
     * Number of previous passwords to check for reuse.
     * Default is 5 as per security best practices.
     */
    private static final int PASSWORD_HISTORY_LIMIT = 5;

    /**
     * Token expiration time in minutes.
     */
    private static final int TOKEN_EXPIRATION_MINUTES = 30;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Lazily computed hash used to spend the same Argon2 time when the account does not exist. */
    private volatile String dummyHash;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Registers a new user and creates an initial profile if applicable.
     * <p>
     * For patients, a basic profile is automatically generated using the 
     * information provided in the registration request.
     * </p>
     *
     * @param request the {@link RegistrationRequest} details
     * @return the saved {@link Login} entity
     * @throws RuntimeException if the email is already in use
     */
    private static String norm(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Case-insensitive account lookup; ambiguous (duplicate-by-case) matches are treated as not found. */
    private Optional<Login> lookup(String normalizedEmail) {
        Optional<Login> exact = loginRepository.findByEmail(normalizedEmail);
        if (exact.isPresent()) {
            return exact;
        }
        try {
            return loginRepository.findByEmailIgnoreCase(normalizedEmail);
        } catch (org.springframework.dao.IncorrectResultSizeDataAccessException e) {
            logger.warn("Multiple accounts differ only by e-mail case; refusing to guess which one is meant");
            return Optional.empty();
        }
    }

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode("timing-equalisation-password");
            dummyHash = h;
        }
        return h;
    }

    /** OTPs are stored only as a keyed digest; the account id acts as the salt. */
    public static String hashOtp(String otp, Long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((otp + ":" + userId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Transactional
    public Login registerUser(RegistrationRequest request, String ipAddress) {
        rateLimiterService.checkIpLimit("register", ipAddress, 10, java.time.Duration.ofHours(1));
        return registerUser(request);
    }

    /**
     * Registers a patient. If the e-mail is already taken nothing is created and {@code null} is returned,
     * so the public endpoint cannot be used to discover which addresses have accounts.
     */
    @Transactional
    public Login registerUser(RegistrationRequest request) {
        String email = norm(request.getEmail());
        Role role = request.getRole() != null ? request.getRole() : Role.PATIENT;

        if (lookup(email).isPresent()) {
            logEvent(email, "REGISTRATION_FAILED", "UNKNOWN", "UNKNOWN", "Email already taken");
            return null;
        }

        String hash = passwordEncoder.encode(request.getPassword());

        Login newUser = new Login();
        newUser.setEmail(email);
        newUser.setPasswordHash(hash);
        newUser.setRole(role);

        if (role == Role.DOCTOR || role == Role.ADMIN) {
            newUser.setTwoFactorEnabled(true);
        }

        Login savedUser = loginRepository.save(newUser);

        if (role == Role.PATIENT) {
            String fullName = request.getFullName().trim();
            int space = fullName.indexOf(' ');
            PatientProfile profile = new PatientProfile();
            profile.setUser(savedUser);
            profile.setFirstName(space > 0 ? fullName.substring(0, space) : fullName);
            profile.setLastName(space > 0 ? fullName.substring(space + 1).trim() : "-");
            profile.setDateOfBirth(request.getDateOfBirth());
            profile.setAddress(request.getAddress());

            patientProfileRepository.save(profile);
        }

        logEvent(email, "USER_REGISTERED", "UNKNOWN", "UNKNOWN", "User registered with role: " + role);
        return savedUser;
    }

    /**
     * Authenticates a user and generates access and refresh tokens.
     * <p>
     * Includes multi-factor authentication (MFA) logic for staff roles, 
     * rate limiting for security, and session management.
     * </p>
     *
     * @param email the user's email
     * @param rawPassword the user's plain-text password
     * @param ipAddress the originating IP address for audit and security
     * @param userAgent the user's browser agent for session tracking
     * @return a {@link LoginResponse} containing tokens or MFA status
     * @throws RuntimeException for invalid credentials or locked accounts
     */
    public LoginResponse login(String emailInput, String rawPassword, String ipAddress, String userAgent) {
        final String email = norm(emailInput);

        try {
            rateLimiterService.checkIpLimit("login", ipAddress, 30, java.time.Duration.ofMinutes(10));

            rateLimiterService.checkLoginAttempts(email, ipAddress, userAgent);

            Login user = lookup(email)
                    .orElseThrow(() -> {
                        // Spend the same Argon2 time as a real check so response timing does not reveal the account.
                        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash());
                        logEvent(email, "LOGIN_FAILED", ipAddress, userAgent, "User not found");
                        rateLimiterService.registerFailedLogin(email, ipAddress, userAgent);
                        return new com.securehealth.backend.exception.BadRequestException("Invalid credentials");
                    });

            if (Boolean.TRUE.equals(user.getArchived())) {
                passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash());
                logEvent(email, "LOGIN_FAILED", ipAddress, userAgent, "Account disabled");
                throw new com.securehealth.backend.exception.BadRequestException("Invalid credentials");
            }
            if (user.isLocked()) {
                passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash());
                logEvent(email, "LOGIN_FAILED", ipAddress, userAgent, "Account locked");
                throw new com.securehealth.backend.exception.BadRequestException("Invalid credentials");
            }

            if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                rateLimiterService.registerFailedLogin(email, ipAddress, userAgent);
                logEvent(email, "LOGIN_FAILED", ipAddress, userAgent, "Invalid password");
                throw new com.securehealth.backend.exception.BadRequestException("Invalid credentials");
            }

            if (user.isTwoFactorEnabled()) {

                String otp = generateOtp();
                String pendingToken = generateSecureToken();
                user.setOtp(hashOtp(otp, user.getUserId()));
                user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
                user.setOtpPendingTokenHash(hashToken(pendingToken));
                loginRepository.save(user);

                emailService.sendOtp(user.getEmail(), otp);

                logEvent(email, "OTP_REQUIRED", ipAddress, userAgent, "OTP sent to email");
                // No tokens yet: the caller must present the OTP together with the pending token
                return new LoginResponse(null, null, user.getRole().name(), "OTP_REQUIRED", user.getUserId(), pendingToken);
            }

            user.setLastLoginAt(LocalDateTime.now());
            loginRepository.save(user);
            // Keep the stored hash current: re-hash with today's Argon2 parameters when they were strengthened.
            if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(rawPassword));
                loginRepository.save(user);
            }
            String refreshToken = jwtUtil.generateRefreshToken();
            String refreshTokenHash = hashToken(refreshToken);

            Session session = new Session();
            session.setUser(user);
            session.setRefreshTokenHash(refreshTokenHash);
            session.setIpAddress(ipAddress);
            session.setUserAgent(userAgent);
            session.setExpiresAt(LocalDateTime.now().plusDays(7));
            sessionRepository.save(session);
            String accessToken = issueAccessToken(user, session);

            logEvent(email, "LOGIN_SUCCESS", ipAddress, userAgent, "Standard login");

            rateLimiterService.resetLoginAttempts(email);
            tokenBlacklistService.updateLastActive(idleKey(user, session));

            return new LoginResponse(accessToken, refreshToken, user.getRole().name(), "LOGIN_SUCCESS",  user.getUserId());

        } catch (RuntimeException ex) {

            // Log unexpected system errors
            logEvent(email, "SYSTEM_ERROR", ipAddress, userAgent, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Verifies a multi-factor authentication (MFA) OTP and completes login.
     *
     * @param email the user's email
     * @param otp the 6-digit one-time password provided by the user
     * @param ipAddress the originating IP address
     * @param userAgent the user's browser agent
     * @return a {@link LoginResponse} containing generated tokens
     * @throws RuntimeException if the OTP is invalid or expired
     */
    public LoginResponse verifyOtp(String emailInput, String otp, String pendingToken, String ipAddress, String userAgent) {
        final String email = norm(emailInput);

        try {

            rateLimiterService.checkOtpAttempts(email, ipAddress, userAgent);

            Login user = lookup(email)
                    .orElseThrow(() -> new com.securehealth.backend.exception.BadRequestException("Invalid or expired OTP"));

            boolean pendingOk = pendingToken != null
                    && constantTimeEquals(hashToken(pendingToken), user.getOtpPendingTokenHash());
            boolean otpOk = pendingOk
                    && user.getOtp() != null && otp != null
                    && constantTimeEquals(hashOtp(otp, user.getUserId()), user.getOtp())
                    && user.getOtpExpiry() != null && user.getOtpExpiry().isAfter(LocalDateTime.now());

            if (otpOk) {

                user.setOtp(null);
                user.setOtpExpiry(null);
                user.setOtpPendingTokenHash(null);
                user.setLastLoginAt(LocalDateTime.now());
                loginRepository.save(user);

                rateLimiterService.resetOtpAttempts(email);

                String refreshToken = jwtUtil.generateRefreshToken();
                String refreshTokenHash = hashToken(refreshToken);

                Session session = new Session();
                session.setUser(user);
                session.setRefreshTokenHash(refreshTokenHash);
                session.setIpAddress(ipAddress);
                session.setUserAgent(userAgent);
                session.setExpiresAt(LocalDateTime.now().plusDays(7));
                sessionRepository.save(session);
                String accessToken = issueAccessToken(user, session);

                tokenBlacklistService.updateLastActive(idleKey(user, session));
                logEvent(email, "LOGIN_SUCCESS", ipAddress, userAgent, "2FA Verified");
                return new LoginResponse(accessToken, refreshToken, user.getRole().name(), "LOGIN_SUCCESS", user.getUserId());
            }
            rateLimiterService.registerFailedOtp(email, ipAddress, userAgent);
            logEvent(email, "LOGIN_FAILED", ipAddress, userAgent, "Invalid/Expired OTP");

            throw new com.securehealth.backend.exception.BadRequestException("Invalid or expired OTP");

        } catch (RuntimeException ex) {

            logEvent(email, "SYSTEM_ERROR", ipAddress, userAgent, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Revokes a user's session and blacklists the provided access token.
     *
     * @param accessToken the active JWT to blacklist
     * @param refreshToken the refresh token to revoke
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // 1. Blacklist the Access Token (Task #19286)
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            String jwt = accessToken.substring(7);
            try {
                // Calculate remaining time
                java.util.Date expiration = jwtUtil.extractExpiration(jwt);
                long remainingMillis = expiration.getTime() - System.currentTimeMillis();

                tokenBlacklistService.blacklistToken(jwt, remainingMillis);
            } catch (Exception e) {
                // Token might already be expired, which is fine
            }
        }

        // 2. Revoke the Refresh Token
        if (refreshToken != null) {
            String hash = hashToken(refreshToken);
            sessionRepository.findByRefreshTokenHash(hash).ifPresent(session -> {
                session.setRevoked(true);
                sessionRepository.save(session);

                // Clear the idle tracker
                tokenBlacklistService.clearIdleSession(idleKey(session.getUser(), session));

                logEvent(session.getUser().getEmail(), "LOGOUT", session.getIpAddress(), session.getUserAgent(),
                        "User initiated logout");
            });
        }
    }

    /**
     * Re-sends a fresh OTP to a user who already passed the password step and is
     * waiting on 2FA verification (mirrors the OTP-generation branch of {@link #login}).
     *
     * @param email the email of the user awaiting OTP verification
     * @throws RuntimeException if the user does not exist or does not require 2FA
     */
    @Transactional
    public void resendOtp(String emailInput, String pendingToken) {
        final String email = norm(emailInput);
        rateLimiterService.checkOtpResend(email, "UNKNOWN", "UNKNOWN");
        Login user = lookup(email)
                .orElseThrow(() -> new com.securehealth.backend.exception.BadRequestException("The sign-in attempt has expired. Please sign in again."));

        // Only a caller that already passed the password step holds the pending token.
        boolean pendingOk = pendingToken != null
                && constantTimeEquals(hashToken(pendingToken), user.getOtpPendingTokenHash())
                && user.getOtpExpiry() != null
                && user.getOtpExpiry().plusMinutes(5).isAfter(LocalDateTime.now());
        if (!pendingOk) {
            throw new com.securehealth.backend.exception.BadRequestException("The sign-in attempt has expired. Please sign in again.");
        }

        String otp = generateOtp();
        user.setOtp(hashOtp(otp, user.getUserId()));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        loginRepository.save(user);

        emailService.sendOtp(user.getEmail(), otp);
        logEvent(email, "OTP_RESENT", "UNKNOWN", "UNKNOWN", "OTP re-sent to email");
    }

    /**
     * Enables Two-Factor Authentication for a user.
     *
     * @param email the email of the user to enable 2FA for
     */
    @Transactional
    public void enableTwoFactorAuth(String email) {
        Login user = loginRepository.findByEmail(email)
                .orElseThrow(() -> new com.securehealth.backend.exception.NotFoundException("User not found"));

        user.setTwoFactorEnabled(true);
        loginRepository.save(user);

        logEvent(email, "2FA_ENABLED", "UNKNOWN", "UNKNOWN", "User manually enabled 2FA");
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing token");
        }
    }

    /**
     * Generates a 6-digit numeric One-Time Password (OTP).
     * <p>
     * The OTP is used for step-up authentication during 2FA login.
     * </p>
     *
     * @return A randomly generated 6-digit OTP as a String.
     */
    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    // ==================== PASSWORD RECOVERY METHODS ====================

    /**
     * Initiates the password reset process.
     * <p>
     * Generates a secure token, stores its hash in the database,
     * and sends a reset link to the user's email.
     * </p>
     *
     * @param email The email address of the user requesting password reset.
     * @throws RuntimeException if email is not registered (for security, returns
     *                          same message).
     */
    @Transactional
    public void initiatePasswordReset(String email, String ipAddress) {
        rateLimiterService.checkIpLimit("forgot-password", ipAddress, 10, java.time.Duration.ofHours(1));
        initiatePasswordReset(email);
    }

    @Transactional
    public void initiatePasswordReset(String emailInput) {
        String email = norm(emailInput);
        // Find user by email - don't reveal if email exists for security
        Optional<Login> userOpt = lookup(email);

        if (userOpt.isEmpty()) {
            // For security, we don't reveal if email exists or not
            // Just log and return silently
            logEvent(email, "PASSWORD_RESET_REQUEST", "UNKNOWN", "UNKNOWN", "User not found");
            return;
        }

        Login user = userOpt.get();

        // Invalidate any existing tokens for this user
        resetTokenRepository.invalidateAllTokensForUser(user);

        // Generate secure token
        String token = generateSecureToken();
        String tokenHash = hashToken(token);

        // Create token entity with 30-minute expiration
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES);
        PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, expiresAt);
        resetTokenRepository.save(resetToken);

        // Build reset link and send email
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(email, resetLink);
        logEvent(email, "PASSWORD_RESET_INITIATED", "UNKNOWN", "UNKNOWN", "Reset link sent to email");
    }

    /**
     * Validates a password reset token.
     * <p>
     * Checks if the token exists, has not been used, and has not expired.
     * </p>
     *
     * @param token The raw token from the reset link.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateResetToken(String token) {
        String tokenHash = hashToken(token);
        Optional<PasswordResetToken> resetTokenOpt = resetTokenRepository.findValidToken(tokenHash,
                LocalDateTime.now());
        return resetTokenOpt.isPresent();
    }

    /**
     * Resets the user's password using a valid reset token.
     * <p>
     * Validates the token, checks for password reuse, updates the password,
     * and stores the old password in history.
     * </p>
     *
     * @param token       The raw token from the reset link.
     * @param newPassword The new password to set.
     * @throws RuntimeException if token is invalid, expired, or password was
     *                          previously used.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String tokenHash = hashToken(token);

        // Find and validate token
        PasswordResetToken resetToken = resetTokenRepository
                .findValidToken(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new com.securehealth.backend.exception.BadRequestException("Invalid or expired reset token"));

        Login user = resetToken.getUser();

        // Check password strength (additional validation beyond DTO)
        validatePasswordStrength(newPassword);

        // Check for password reuse
        if (isPasswordPreviouslyUsed(user, newPassword)) {
            logEvent(user.getEmail(), "PASSWORD_RESET_FAILED", "UNKNOWN", "UNKNOWN", "Password reuse attempt");
            throw new com.securehealth.backend.exception.BadRequestException("Cannot reuse a recent password. Please choose a different password.");
        }

        // Store current password in history before changing
        savePasswordToHistory(user, user.getPasswordHash());

        // Hash and update new password
        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newPasswordHash);
        loginRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        // Invalidate all active sessions for security
        invalidateAllUserSessions(user);
        logEvent(user.getEmail(), "PASSWORD_RESET_SUCCESS", "UNKNOWN", "UNKNOWN", "Password reset successfully");
    }

    /**
     * Checks if a password was previously used by the user.
     * <p>
     * Compares against the last 5 passwords stored in history.
     * </p>
     *
     * @param user        The user whose history to check.
     * @param newPassword The new password to verify.
     * @return true if the password was previously used, false otherwise.
     */
    private boolean isPasswordPreviouslyUsed(Login user, String newPassword) {
        // Check current password
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            return true;
        }

        // Check password history
        List<PasswordHistory> history = passwordHistoryRepository
                .findRecentPasswords(user, PASSWORD_HISTORY_LIMIT);

        for (PasswordHistory entry : history) {
            if (passwordEncoder.matches(newPassword, entry.getPasswordHash())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Saves a password hash to the user's password history.
     *
     * @param user         The user whose password is being stored.
     * @param passwordHash The hashed password to store.
     */
    private void savePasswordToHistory(Login user, String passwordHash) {
        PasswordHistory history = new PasswordHistory(user, passwordHash);
        passwordHistoryRepository.save(history);
    }

    /**
     * Validates password strength beyond minimum length.
     * <p>
     * NIST 800-63B compliant validation:
     * - Minimum 12 characters
     * - No common patterns (optional, can be extended)
     * </p>
     *
     * @param password The password to validate.
     * @throws RuntimeException if password doesn't meet requirements.
     */
    void validatePasswordStrength(String password) {
        if (password == null || password.length() < 12) {
            throw new com.securehealth.backend.exception.BadRequestException("Password must be at least 12 characters long");
        }

        // Check for common weak patterns
        String lowerPassword = password.toLowerCase();
        String[] weakPatterns = { "password", "123456", "qwerty", "admin", "letmein" };

        for (String pattern : weakPatterns) {
            if (lowerPassword.contains(pattern)) {
                throw new com.securehealth.backend.exception.BadRequestException("Password contains a common weak pattern");
            }
        }
    }

    /**
     * Invalidates all active sessions for a user.
     * <p>
     * Called after password reset for security.
     * Forces re-authentication on all devices.
     * </p>
     *
     * @param user The user whose sessions should be invalidated.
     */
    private void invalidateAllUserSessions(Login user) {
        for (Session active : sessionRepository.findActiveSessionsByUserOrderByCreatedAtAsc(user)) {
            tokenBlacklistService.clearIdleSession(idleKey(user, active));
        }
        sessionRepository.revokeAllUserSessions(user.getUserId());
        tokenBlacklistService.clearIdleSession(user.getEmail());
    }

    /**
     * Generates a cryptographically secure random token.
     *
     * @return A URL-safe Base64 encoded token.
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Rotates a refresh token to generate new session tokens.
     * <p>
     * Implements rotation logic to prevent token theft and session hijacking.
     * Revokes all sessions if a reuse attempt is detected.
     * </p>
     *
     * @param oldRefreshToken the refresh token to rotate
     * @param ipAddress the current IP address
     * @param userAgent the current user agent
     * @return a {@link LoginResponse} with new tokens
     * @throws RuntimeException if the token is invalid or suspected theft occurs
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public LoginResponse refreshToken(String oldRefreshToken, String ipAddress, String userAgent) {
        // 1. Hash the incoming token to find it in DB
        String hash = hashToken(oldRefreshToken);

        Session session = sessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> {
                    logEvent("UNKNOWN", "INVALID_REFRESH_TOKEN", ipAddress, userAgent,
                            "Invalid refresh token attempt");
                    return new com.securehealth.backend.exception.BadRequestException("Invalid refresh token");
                });

        // 2. SECURITY CHECK: Reuse Detection (Theft)
        if (session.isRevoked()) {
            // ALARM: Someone is trying to use a dead token!
            // This means the legitimate user likely already rotated it, and now a hacker is
            // trying the old one.
            logEvent(session.getUser().getEmail(), "TOKEN_THEFT_DETECTED", ipAddress, userAgent,
                    "Reuse of revoked token attempted");

            // Nuclear Option: Kill ALL sessions for this user to force re-login
            sessionRepository.revokeAllUserSessions(session.getUser().getUserId());

            throw new com.securehealth.backend.exception.BadRequestException("Security Alert: Session revoked due to suspected theft.");
        }

        // 2a. Disabled or locked accounts cannot renew a session.
        Login owner = session.getUser();
        if (Boolean.TRUE.equals(owner.getArchived()) || owner.isLocked()) {
            session.setRevoked(true);
            sessionRepository.save(session);
            throw new com.securehealth.backend.exception.BadRequestException("Invalid refresh token");
        }

        // 2b. Idle timeout: an idle session cannot be silently renewed with the long-lived refresh cookie.
        if (tokenBlacklistService.isSessionIdle(idleKey(session.getUser(), session))) {
            session.setRevoked(true);
            sessionRepository.save(session);
            logEvent(session.getUser().getEmail(), "SESSION_IDLE_EXPIRED", ipAddress, userAgent,
                    "Refresh refused: idle timeout exceeded");
            throw new com.securehealth.backend.exception.BadRequestException("Session expired due to inactivity. Please login again.");
        }

        // 3. Check Expiry
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new com.securehealth.backend.exception.BadRequestException("Refresh token expired. Please login again.");
        }

        // 4. ROTATE: Revoke the old token
        session.setRevoked(true);
        sessionRepository.save(session);

        // 5. Generate NEW Tokens
        Login user = session.getUser();
        String newRefreshToken = jwtUtil.generateRefreshToken(); // UUID

        // 6. Save NEW Session (the access token is bound to it)
        Session newSession = createSession(user, newRefreshToken, ipAddress, userAgent);
        String newAccessToken = issueAccessToken(user, newSession);
        tokenBlacklistService.clearIdleSession(idleKey(user, session));

        // Log the success
        logEvent(user.getEmail(), "TOKEN_REFRESHED", ipAddress, userAgent, "Token rotated successfully");

        return new LoginResponse(newAccessToken, newRefreshToken, user.getRole().name(), "SUCCESS",user.getUserId());
    }

    private Session createSession(Login user, String refreshToken, String ipAddress, String userAgent) {

        List<Session> activeSessions = sessionRepository.findActiveSessionsByUserOrderByCreatedAtAsc(user);

        // If they have 3 or more active sessions, revoke the oldest ones until they
        // have 2 left
        if (activeSessions.size() >= MAX_ACTIVE_SESSIONS) {
            int sessionsToKill = (activeSessions.size() - MAX_ACTIVE_SESSIONS) + 1;
            for (int i = 0; i < sessionsToKill; i++) {
                Session oldestSession = activeSessions.get(i);
                oldestSession.setRevoked(true);
                sessionRepository.save(oldestSession);

                logEvent(user.getEmail(), "SESSION_TERMINATED", oldestSession.getIpAddress(),
                        oldestSession.getUserAgent(), "Max concurrent sessions exceeded. Oldest session revoked.");
            }
        }

        String refreshTokenHash = hashToken(refreshToken);
        Session session = new Session();
        session.setUser(user);
        session.setRefreshTokenHash(refreshTokenHash);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        sessionRepository.save(session);

        // Start the idle tracker for this new session
        tokenBlacklistService.updateLastActive(idleKey(user, session));
        return session;
    }

    /** Idle tracking is per login session; the e-mail is only a fallback when the session has no id yet. */
    private static String idleKey(Login user, Session session) {
        return session != null && session.getId() != null ? "s:" + session.getId() : user.getEmail();
    }

    private String issueAccessToken(Login user, Session session) {
        String sid = session != null && session.getId() != null ? String.valueOf(session.getId()) : null;
        return jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getUserId(), sid);
    }

    private void logEvent(String email, String action, String ip, String agent, String details) {
        try {

            AuditLog log = new AuditLog(email, action, ip, agent, details);
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Failsafe: Logging should never break the actual login process
            logger.error("Failed to persist audit log entry for action {}", action, e);
        }
    }
}