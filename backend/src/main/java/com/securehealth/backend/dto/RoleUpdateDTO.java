package com.securehealth.backend.dto;

import lombok.Data;

/**
 * Data Transfer Object for updating a user's role.
 * <p>
 * Simple DTO used by administrators to change the role of a staff member.
 * </p>
 */
@Data
public class RoleUpdateDTO {
    private String newRole;
}