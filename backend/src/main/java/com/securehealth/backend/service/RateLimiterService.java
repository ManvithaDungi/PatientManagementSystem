package com.securehealth.backend.service;

import com.securehealth.backend.model.AuditLog;
import com.securehealth.backend.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for enforcing security rate limiting and account locking.
 * <p>
 * Uses Redis to track failed login and OTP attempts, providing 
 * temporary lockouts to prevent brute-force attacks.
 * </p>
 */
@Service
public class RateLimiterService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RateLimiterService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Validates that a user account is not currently locked due to failed attempts.
     *
     * @param email the user's email
     * @param ipAddress original IP address for auditing
     * @param userAgent original user agent for auditing
     * @throws RuntimeException if the account is locked
     */
    public void checkLoginAttempts(String email, String ipAddress, String userAgent) {
        String lockKey = "auth:lock:" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            logEvent(email, "LOGIN_BLOCKED", ipAddress, userAgent, "Account is temporarily locked");
            throw new com.securehealth.backend.exception.BadRequestException("Account is temporarily locked. Try again in 30 minutes.");
        }
    }


    /**
     * Increments the failure count for a login attempt.
     * <p>
     * Triggers a 30-minute account lock if the threshold is reached.
     * </p>
     *
     * @param email the user's email
     * @param ipAddress originating IP address
     * @param userAgent originating user agent
     * @throws RuntimeException if the lockout threshold is reached
     */
    public void registerFailedLogin(String email, String ipAddress, String userAgent) {
        String failKey = "auth:fail:" + email;
        String lockKey = "auth:lock:" + email;

        Long attempts = redisTemplate.opsForValue().increment(failKey);
        
        // If it's the first failure, set expiry to 15 mins
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(failKey, Duration.ofMinutes(15));
        }


        if (attempts != null && attempts >= 5) {
            redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(30));
            redisTemplate.delete(failKey); // Reset counter so it starts fresh after unlock
            
            logEvent(email, "ACCOUNT_LOCKED", ipAddress, userAgent, "Locked due to 5 failed attempts");
            throw new com.securehealth.backend.exception.BadRequestException("Too many failed attempts. Account locked for 30 minutes.");
        }
    }
        
    /**
     * Resets the failed login counter for a user (e.g., after a successful login).
     *
     * @param email the user's email
     */
    public void resetLoginAttempts(String email) {
        redisTemplate.delete("auth:fail:" + email);
    }


    /**
     * Validates that a user has not exceeded the OTP verification attempt limit.
     *
     * @param email the user's email
     * @param ipAddress originating IP address
     * @param userAgent originating user agent
     * @throws RuntimeException if the attempt limit is reached
     */
    public void checkOtpAttempts(String email, String ipAddress, String userAgent) {
        String key = "otp:attempt:" + email;
        String attemptsStr = redisTemplate.opsForValue().get(key);
        
        if (attemptsStr != null && Integer.parseInt(attemptsStr) >= 3) {
            logEvent(email, "OTP_BLOCKED", ipAddress, userAgent, "Blocked due to too many invalid OTP attempts");
            throw new com.securehealth.backend.exception.BadRequestException("Too many invalid OTP attempts. Please request a new OTP.");
        }
    }
    
    /**
     * Increments the failure count for an OTP verification attempt.
     *
     * @param email the user's email
     * @param ipAddress originating IP address
     * @param userAgent originating user agent
     * @throws RuntimeException if the attempt limit is reached
     */
    public void registerFailedOtp(String email, String ipAddress, String userAgent) {
        String key = "otp:attempt:" + email;

        Long attempts = redisTemplate.opsForValue().increment(key);
        
        // Expire the block after 5 minutes
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(5));
        }

        if (attempts != null && attempts >= 3) {
            logEvent(email, "OTP_LIMIT_REACHED", ipAddress, userAgent, "Limit reached for OTP attempts");
            throw new com.securehealth.backend.exception.BadRequestException("Too many invalid OTP attempts. Please request a new OTP.");
        }
    }

    static final int MAX_OTP_RESENDS = 5;

    /**
     * Limits how often a one-time code can be re-sent to an address: at most
     * {@value #MAX_OTP_RESENDS} per 15 minutes. The endpoint is public, so this
     * also stops it being used to flood someone's inbox.
     *
     * @param email the address the code would be sent to
     * @throws RuntimeException if the limit has been reached
     */
    public void checkOtpResend(String email, String ipAddress, String userAgent) {
        String key = "otp:resend:" + email.trim().toLowerCase();

        Long sends = redisTemplate.opsForValue().increment(key);
        if (sends != null && sends == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(15));
        }

        if (sends != null && sends > MAX_OTP_RESENDS) {
            logEvent(email, "OTP_RESEND_LIMIT", ipAddress, userAgent, "Too many OTP resend requests");
            throw new com.securehealth.backend.exception.BadRequestException("Too many OTP requests. Please wait a few minutes and try again.");
        }
    }

    /**
     * Resets the OTP attempt counter for a user.
     *
     * @param email the user's email
     */
    public void resetOtpAttempts(String email) {
        redisTemplate.delete("otp:attempt:" + email);
    }

    private void logEvent(String email, String action, String ip, String agent, String details) {
        try {
            AuditLog log = new AuditLog(email, action, ip, agent, details);
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to persist audit log entry for action {}", action, e);
        }
    }

    /**
     * Fixed-window per-IP limit for unauthenticated endpoints (login, register, forgot-password, ...).
     * Throws {@link com.securehealth.backend.exception.TooManyRequestsException} once {@code max} calls were
     * made in {@code window}. A missing Redis counter simply allows the call (the limiter never blocks on errors).
     */
    public void checkIpLimit(String bucket, String ip, int max, Duration window) {
        String key = "ratelimit:" + bucket + ":" + (ip == null ? "unknown" : ip);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > max) {
            logEvent("IP:" + ip, "RATE_LIMITED", ip, "UNKNOWN", "Bucket " + bucket + " exceeded " + max + " per " + window);
            throw new com.securehealth.backend.exception.TooManyRequestsException(
                    "Too many requests. Please wait a few minutes and try again.");
        }
    }
}
