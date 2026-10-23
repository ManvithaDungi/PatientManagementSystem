package com.securehealth.backend.dto;

import lombok.Data;

/**
 * Data Transfer Object representing internal staff information.
 * <p>
 * Transports basic staff details such as user ID, email, and current role 
 * for administrative purposes.
 * </p>
 */
@Data
public class StaffDTO {
    private Long userId;
    private String email;
    private String role;
    private boolean enabled = true;
    private java.time.LocalDateTime lastLoginAt;
}