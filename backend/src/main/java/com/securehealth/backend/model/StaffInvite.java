package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A single-use, expiring invitation for a staff member (doctor, nurse, lab
 * technician or admin) to create their account.
 * <p>
 * Only the SHA-256 hash of the invite token is stored; the raw token exists
 * solely inside the emailed link. The {@code role} stored here is authoritative:
 * whoever accepts the invite receives exactly this role.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "staff_invites", indexes = {
        @Index(name = "idx_staff_invites_email", columnList = "email")
})
public class StaffInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    private boolean revoked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private Login invitedBy;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isUsable(LocalDateTime now) {
        return !used && !revoked && expiresAt.isAfter(now);
    }
}
