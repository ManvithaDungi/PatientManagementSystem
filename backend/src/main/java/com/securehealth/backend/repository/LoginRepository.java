package com.securehealth.backend.repository;

import com.securehealth.backend.model.Login;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.securehealth.backend.model.Role;

import java.util.Optional;
import java.util.List;

/**
 * Repository interface for {@link Login} entities, handling user authentication and core identity.
 * <p>
 * Provides essential methods for finding users by email, checking for existence, 
 * and retrieving users by role for security and administrative purposes.
 * </p>
 */
@Repository
public interface LoginRepository extends JpaRepository<Login, Long> {

    /**
     * Finds a user by their email address.
     * <p>
     * Used during authentication to retrieve the password hash and role.
     * </p>
     *
     * @param email The unique email address to search for.
     * @return An Optional containing the Login entity if found, or empty if not.
     */
    Optional<Login> findByEmail(String email);

    /** Case-insensitive lookup; throws IncorrectResultSizeDataAccessException if several accounts match. */
    Optional<Login> findByEmailIgnoreCase(String email);

    /**
     * Checks if a user exists with the given email.
     * <p>
     * Used during registration to prevent duplicate accounts.
     * </p>
     *
     * @param email The email address to check.
     * @return true if the email is already registered, false otherwise.
     */
    boolean existsByEmail(String email);

    /**
     * Counts the total number of users assigned to a specific role.
     * <p>
     * Used for administrative metrics and dashboard reporting.
     * </p>
     * @param role The Role enum value (e.g., Role.DOCTOR).
     * @return The total count of users with the specified role.
     */
    long countByRole(Role role);

    @org.springframework.data.jpa.repository.Query("select count(l) from Login l where l.role = :role and (l.archived is null or l.archived = false)")
    long countActiveByRole(@org.springframework.data.repository.query.Param("role") Role role);

    /**
     * Retrieves a list of users who do not have the specified role.
     * <p>
     * Typically used to fetch all staff members (Admins, Doctors, Nurses) by excluding "PATIENT".
     * </p>
     * @param role The role to exclude from the results.
     * @return A list of Login entities that do not match the given role.
     */
    List<Login> findByRoleNot(String role);

    /**
     * Retrieves a list of users who do not have the specified role using the Role Enum.
     * 
     * @param role The Role Enum to exclude from the results.
     * @return A list of Login entities that do not match the given role.
     */
    List<Login> findByRoleNot(Role role);
}