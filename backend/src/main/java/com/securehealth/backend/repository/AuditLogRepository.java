package com.securehealth.backend.repository;

import com.securehealth.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository interface for {@link AuditLog} entities.
 * <p>
 * Used primarily for administrative review of system events. Listing goes
 * through {@link JpaSpecificationExecutor} so that filters (user, time range)
 * and paging are applied by the database; the audit table is append-only and
 * grows without bound, so it must never be loaded whole.
 * </p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByEmailOrderByTimestampDesc(String email);

    /** Newest-first events of the given actions since a point in time (bounded by the pageable). */
    List<AuditLog> findByActionInAndTimestampGreaterThanEqualOrderByTimestampDesc(
            Collection<String> actions, LocalDateTime since, Pageable pageable);

    /** Newest-first events of one action whose details end with the given suffix (e.g. " patient=12"). */
    List<AuditLog> findTop100ByActionAndDetailsEndingWithOrderByTimestampDesc(String action, String suffix);

    /** Number of events per action since a point in time, computed by the database. */
    @Query("select a.action, count(a) from AuditLog a where a.timestamp >= :since group by a.action")
    List<Object[]> countByActionSince(@Param("since") LocalDateTime since);
}
