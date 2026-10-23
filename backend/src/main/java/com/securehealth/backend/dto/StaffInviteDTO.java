package com.securehealth.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Admin-facing view of an invite. Never contains the token or its hash. */
@Data
public class StaffInviteDTO {
    private Long id;
    private String email;
    private String role;
    private String status; // PENDING, EXPIRED, USED, REVOKED
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String invitedBy;
}
