package com.securehealth.backend.model;


import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing the core identity for authentication and authorization.
 * <p>
 * Stores user credentials (email, hashed password), role information, 
 * multi-factor authentication states (OTP), and security-related fields 
 * for account locking and brute-force protection.
 * </p>
 */
@Data // Generates Getters, Setters, toString, Equals, HashCode
@NoArgsConstructor // Generates empty constructor (Required by JPA)
@AllArgsConstructor // Generates full constructor
@Entity
@Table(name = "login")
public class Login {

    /**
     * Unique identifier for the user.
     * Auto-incremented by the database.
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    /**
     * To enable 2FA for doctors and admins
     */
    private boolean twoFactorEnabled;

    /**
     * To authorize the user
     */
    @JsonIgnore
    private String otp;

    /**
     * To check if the otp is valid or not
     */
    @JsonIgnore
    private LocalDateTime otpExpiry;

    // SHA-256 of the one-time token that binds a pending 2FA login to its OTP (null when none is pending)
    @JsonIgnore
    private String otpPendingTokenHash;

    /**
     * User's unique email address.
     * Used as the username for login.
     */
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * Securely hashed password (Argon2).
     * <p><b>SECURITY WARNING:</b> Never store plaintext passwords here.</p>
     */
    @Column(nullable = false)
    @JsonIgnore
    private String passwordHash;

    /**
     * The user's role in the system.
     * Examples: PATIENT, DOCTOR, ADMIN.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // --- Active Defense Fields (Epic 5) ---

    /**
     * Tracks consecutive failed login attempts.
     * Used to trigger temporary account locking.
     */
    @JsonIgnore
    private int failedAttempts = 0;

    /**
     * Indicates if the account is currently locked due to brute-force attempts.
     */
    private boolean isLocked = false;

    /**
     * Timestamp indicating when the account will automatically unlock.
     * Null if the account is not locked.
     */
    @JsonIgnore
    private LocalDateTime lockoutUntil;

    /**
     * Audit timestamp for when the account was created.
     * Cannot be updated after creation.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Track last login time for archival purposes
    private LocalDateTime lastLoginAt;

    // Whether the user has been archived due to inactivity
    @Column(columnDefinition = "boolean default false")
    private Boolean archived = false;

    // Compatibility methods for tests expecting snake_case naming
    public Long getUser_id() {
        return userId;
    }

    public void setUser_id(Long userId) {
        this.userId = userId;
    }
}