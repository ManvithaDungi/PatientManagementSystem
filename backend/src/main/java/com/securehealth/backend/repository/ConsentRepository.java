package com.securehealth.backend.repository;

import com.securehealth.backend.model.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository interface for {@link Consent} entities.
 * <p>
 * Manages patient data sharing permissions, including active consent checks 
 * and provider-specific access lookups.
 * </p>
 */
@Repository
public interface ConsentRepository extends JpaRepository<Consent, Long> {

    // All consents for a patient
    List<Consent> findByPatient_ProfileIdOrderByGrantedAtDesc(Long profileId);

    // Active consents for a patient
    List<Consent> findByPatient_ProfileIdAndStatus(Long profileId, String status);

    // What a specific provider is allowed to access
    List<Consent> findByGrantedTo_UserIdAndStatus(Long userId, String status);

    // Check if a specific consent already exists
    boolean existsByPatient_ProfileIdAndGrantedTo_UserIdAndConsentTypeAndStatus(
            Long profileId, Long userId, String consentType, String status);

    // True only for ACTIVE consents that have not passed their optional expiry.
    @Query("select count(c) > 0 from Consent c where c.patient.profileId = :patientId "
            + "and c.grantedTo.userId = :providerId and c.consentType in :types "
            + "and c.status = 'ACTIVE' and (c.expiresAt is null or c.expiresAt > :now)")
    boolean existsEffective(@Param("patientId") Long patientId, @Param("providerId") Long providerId,
                            @Param("types") Collection<String> types, @Param("now") LocalDateTime now);

    // ACTIVE consents whose expiry has passed (to be flipped to EXPIRED).
    List<Consent> findByStatusAndExpiresAtBefore(String status, LocalDateTime cutoff);
}
