package com.securehealth.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor; // [NEW] Generates constructor with args
import lombok.Data;
import lombok.NoArgsConstructor;  // [NEW] Generates empty constructor

/**
 * Data Transfer Object for login requests.
 * <p>
 * This DTO is used by users to provide their credentials (email and password)
 * for authentication. It includes validation constraints to ensure data integrity.
 * </p>
 */
@Data
@NoArgsConstructor  // Fixes "The constructor LoginRequest() is undefined"
@AllArgsConstructor // Fixes "new LoginRequest(email, pass)" usage
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters long")
    private String password;
}