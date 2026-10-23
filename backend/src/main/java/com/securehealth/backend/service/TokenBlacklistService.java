package com.securehealth.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for managing JWT revocation and idle session tracking.
 * <p>
 * Uses Redis to maintain a blacklist of revoked access tokens and 
 * tracks user activity to enforce idle session timeouts.
 * </p>
 */
/**
 * Service for managing JWT revocation and idle session tracking.
 * <p>
 * Uses Redis to maintain a blacklist of revoked access tokens and 
 * tracks user activity to enforce idle session timeouts.
 * </p>
 */
@Service
public class TokenBlacklistService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Blacklists an access token until its natural expiration time.
     *
     * @param token the JWT to blacklist
     * @param remainingMillis duration in milliseconds until the token expires
     */
    public void blacklistToken(String token, long remainingMillis) {
        if (remainingMillis > 0) {
            String key = "jwt:blacklist:" + digest(token);
            // The token naturally expires after this duration, so we can let Redis drop it then to save memory
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(remainingMillis));
        }
    }

    /**
     * Checks if a specific token has been revoked and blacklisted.
     *
     * @param token the JWT to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isBlacklisted(String token) {
        String key = "jwt:blacklist:" + digest(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }


    /**
     * Updates the last active timestamp for a user's session.
     *
     * @param email the user's email
     */
    /**
     * Updates the last active timestamp for a user's session.
     *
     * @param email the user's email
     */
    public void updateLastActive(String email) {
        String key = "session:active:" + email;
        // Extend the idle timeout by 30 minutes on every request
        redisTemplate.opsForValue().set(key, "active", Duration.ofMinutes(30));
    }

    /**
     * Checks if a user's session has been idle beyond the timeout threshold.
     *
     * @param email the user's email
     * @return true if the session is considered idle, false otherwise
     */
    /**
     * Checks if a user's session has been idle beyond the timeout threshold.
     *
     * @param email the user's email
     * @return true if the session is considered idle, false otherwise
     */
    public boolean isSessionIdle(String email) {
        String key = "session:active:" + email;
        // If the key doesn't exist, they have been idle for > 30 minutes
        return Boolean.FALSE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * Clears the active session status for a user.
     *
     * @param email the user's email
     */
    /**
     * Clears the active session status for a user.
     *
     * @param email the user's email
     */
    public void clearIdleSession(String email) {
        redisTemplate.delete("session:active:" + email);
    }

    /** Redis keys hold a SHA-256 digest so a raw bearer token is never stored (or visible via KEYS/SCAN). */
    static String digest(String token) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
