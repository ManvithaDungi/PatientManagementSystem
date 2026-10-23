package com.securehealth.backend.repository;

import com.securehealth.backend.model.StaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffInviteRepository extends JpaRepository<StaffInvite, Long> {

    Optional<StaffInvite> findByTokenHash(String tokenHash);

    List<StaffInvite> findByEmailIgnoreCaseAndUsedFalseAndRevokedFalse(String email);

    List<StaffInvite> findByUsedFalseAndRevokedFalseOrderByCreatedAtDesc();
}
