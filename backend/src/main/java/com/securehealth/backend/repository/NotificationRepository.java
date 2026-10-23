package com.securehealth.backend.repository;

import com.securehealth.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUser_UserIdOrderByCreatedAtDesc(Long userId);

    long countByUser_UserIdAndReadFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
