package com.securehealth.backend.repository;

import com.securehealth.backend.model.HandoverNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link HandoverNote} entities.
 * <p>
 * Enables nurses to retrieve handover notes by author or shift direction, 
 * ensuring continuity of care between shifts.
 * </p>
 */
@Repository
public interface HandoverNoteRepository extends JpaRepository<HandoverNote, Long> {
    List<HandoverNote> findByAuthor_UserIdOrderByTimestampDesc(Long authorId);
    List<HandoverNote> findByShiftDirectionOrderByTimestampDesc(String shiftDirection);
}
