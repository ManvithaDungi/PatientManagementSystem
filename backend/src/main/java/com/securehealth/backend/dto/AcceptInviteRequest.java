package com.securehealth.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Invitee's request to complete account creation. The email and role come from
 * the invite itself, never from this body.
 */
@Data
public class AcceptInviteRequest {

    @NotBlank(message = "Invite token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters long")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    /** Required when the invited role is DOCTOR. */
    private String specialty;

    private String contactNumber;
}
