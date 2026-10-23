package com.securehealth.backend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Utility class for handling JSON Web Tokens (JWT).
 * <p>
 * This component provides methods for generating, extracting, and validating 
 * access tokens using the JJWT library (0.12.x syntax).
 * </p>
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    // ================== READING TOKEN ==================

    /**
     * Extracts the subject (username/email) from a JWT.
     *
     * @param token the JWT
     * @return the extracted username
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Validates if a token belongs to the given user and is not expired.
     *
     * @param token the JWT
     * @param userDetails the user details to validate against
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // ================== GENERATING TOKEN ==================

    /**
     * Generates a signed access token with custom claims.
     *
     * @param email the user's email (subject)
     * @param role the user's assigned role
     * @param userId the user's internal ID
     * @return a signed JWT string
     */
    public String generateAccessToken(String email, String role, Long userId) {
        return generateAccessToken(email, role, userId, null);
    }

    /**
     * Same as above, additionally binding the token to a login session ({@code sid} claim) so idle tracking
     * is per session instead of per user.
     */
    public String generateAccessToken(String email, String role, Long userId, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        if (sessionId != null) {
            claims.put("sid", sessionId);
        }
        return createToken(claims, email, jwtExpiration);
    }

    /** The {@code sid} claim, or {@code null} for tokens that are not bound to a session. */
    public String extractSessionId(String token) {
        Object sid = extractAllClaims(token).get("sid");
        return sid == null ? null : String.valueOf(sid);
    }

    /**
     * Generates a random UUID-based refresh token.
     *
     * @return a unique refresh token string
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ================== HELPER METHODS (UPDATED FOR 0.12.x) ==================

    /**
     * Generic method to extract a specific claim from a token.
     *
     * @param <T> the type of the claim
     * @param token the JWT
     * @param claimsResolver a function to extract the desired claim
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        // FIX: Used the new parser() syntax for JJWT 0.12.x
        return Jwts.parser()
                .verifyWith(getSigningKey()) // Was setSigningKey()
                .build()
                .parseSignedClaims(token)    // Was parseClaimsJws()
                .getPayload();               // Was getBody()
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from a JWT.
     *
     * @param token the JWT
     * @return the expiration {@link Date}
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims) // Updated syntax for claims
                .subject(subject) // Updated syntax
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS384) // Updated syntax: explicitly use Jwts.SIG
                .compact();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}