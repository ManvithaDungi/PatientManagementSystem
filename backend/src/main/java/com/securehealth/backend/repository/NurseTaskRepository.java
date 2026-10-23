package com.securehealth.backend.repository;

import com.securehealth.backend.model.NurseTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for {@link NurseTask} entities.
 * <p>
 * Supports task management for nursing staff, including counts of pending tasks, 
 * priority-based filtering, and category-specific task lookups.
 * </p>
 */
@Repository
public interface NurseTaskRepository extends JpaRepository<NurseTask, Long> {
    List<NurseTask> findByAssignedNurse_UserIdOrderByDueTimeAsc(Long nurseId);
    long countByAssignedNurse_UserIdAndCompletedFalse(Long nurseId);
    long countByAssignedNurse_UserIdAndCompletedFalseAndDueTimeBefore(Long nurseId, LocalDateTime time);
    long countByAssignedNurse_UserIdAndCompletedFalseAndPriority(Long nurseId, String priority);
    
    // Category specific queries to replace mocked data
    long countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCase(Long nurseId, String category);
    long countByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseAndDueTimeBefore(Long nurseId, String category, LocalDateTime time);
    
    // Optional query to find the earliest due medication
    Optional<NurseTask> findFirstByAssignedNurse_UserIdAndCompletedFalseAndCategoryIgnoreCaseOrderByDueTimeAsc(Long nurseId, String category);
}
