package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity for storing password reset tokens.
 * <p>
 * Securely stores hashed recovery tokens issued to users, including 
 * expiration timestamps and usage status to ensure single-use security.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    /**
     * Unique identifier for the reset token.
     * Auto-incremented by the database.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user requesting the password reset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Login user;

    /**
     * SHA-256 hash of the reset token.
     * <p><b>SECURITY WARNING:</b> Never store plaintext tokens here.</p>
     */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    /**
     * Timestamp indicating when the token will expire.
     * Default expiration is 30 minutes from creation.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Indicates if the token has been used.
     * Once used, the token cannot be reused.
     */
    private boolean used = false;

    /**
     * Audit timestamp for when the token was created.
     * Cannot be updated after creation.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Constructor for creating a new password reset token.
     *
     * @param user      The user requesting the reset
     * @param tokenHash SHA-256 hash of the token
     * @param expiresAt When the token expires
     */
    public PasswordResetToken(Login user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Checks if the token is valid (not expired and not used).
     *
     * @return true if the token can be used, false otherwise
     */
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }
}
