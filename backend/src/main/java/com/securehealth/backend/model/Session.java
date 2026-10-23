package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing an active user session.
 * <p>
 * Tracks refresh tokens, client metadata (IP, User Agent), and 
 * expiration status to manage authentication persistence and security.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_sessions_user_revoked", columnList = "user_id, revoked")
})
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Login user;

    @Column(nullable = false, unique = true)
    private String refreshTokenHash;

    private String ipAddress;

    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

}