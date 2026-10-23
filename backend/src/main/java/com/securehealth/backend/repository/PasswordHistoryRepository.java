package com.securehealth.backend.repository;

import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link PasswordHistory} entities.
 * <p>
 * Facilitates NIST-compliant password reuse policies by allowing retrieval 
 * of a user's recent password hashes for comparison during updates.
 * </p>
 */
@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /**
     * Gets the last N password hashes for a user.
     * <p>
     * Used to check if a new password was previously used.
     * Default policy checks last 5 passwords.
     * </p>
     *
     * @param user  The user whose password history to retrieve.
     * @param limit Maximum number of passwords to return.
     * @return List of PasswordHistory entries, most recent first.
     */
    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user = :user " +
           "ORDER BY ph.createdAt DESC LIMIT :limit")
    List<PasswordHistory> findRecentPasswords(@Param("user") Login user, 
                                               @Param("limit") int limit);

    /**
     * Gets all password history entries for a user.
     * <p>
     * Used for administrative purposes or cleanup.
     * </p>
     *
     * @param user The user whose password history to retrieve.
     * @return List of all PasswordHistory entries for the user.
     */
    List<PasswordHistory> findByUserOrderByCreatedAtDesc(Login user);

    /**
     * Counts the number of password history entries for a user.
     *
     * @param user The user to count for.
     * @return Number of stored password hashes.
     */
    long countByUser(Login user);
}
