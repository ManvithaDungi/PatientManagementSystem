package com.securehealth.backend.dto;

import com.securehealth.backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Data Transfer Object for user registration requests.
 * <p>
 * Contains the necessary information to create a new user account, 
 * including email, password, role, and optional profile details like 
 * full name, date of birth, and address.
 * </p>
 */
@Data
@NoArgsConstructor  // Fixes Test Error
@AllArgsConstructor // Fixes Test Error
public class RegistrationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters long")
    private String password;

    // Never read from the request body: the public endpoint forces PATIENT.
    @JsonIgnore
    private Role role;

    @NotBlank(message = "Full name is required")
    @Size(max = 200, message = "Full name must be at most 200 characters")
    @JsonProperty("full_name")
    private String fullName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    private String address;
}