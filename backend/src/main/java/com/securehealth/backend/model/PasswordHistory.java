package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity for storing previous password hashes to prevent password reuse.
 * <p>
 * Maintains NIST compliance by ensuring users cannot reuse a specified number 
 * of their most recent passwords.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "password_history")
public class PasswordHistory {

    /**
     * Unique identifier for the history entry.
     * Auto-incremented by the database.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user whose password is being stored.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Login user;

    /**
     * Securely hashed password (Argon2).
     * <p><b>SECURITY WARNING:</b> Never store plaintext passwords here.</p>
     */
    @Column(nullable = false)
    private String passwordHash;

    /**
     * Audit timestamp for when the password was set.
     * Cannot be updated after creation.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Constructor for creating a new password history entry.
     *
     * @param user         The user whose password is being stored
     * @param passwordHash The hashed password to store
     */
    public PasswordHistory(Login user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
    }
}
