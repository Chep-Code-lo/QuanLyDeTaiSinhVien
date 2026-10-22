package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.Milestone;
import com.example.qldetai.feature.progress.domain.MilestoneStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select milestone from Milestone milestone where milestone.id = :id")
    Optional<Milestone> findByIdForUpdate(Long id);

    List<Milestone> findByAssignmentIdOrderByDueAtAsc(Long assignmentId);

    List<Milestone> findByStatusInAndDueAtBetween(
            Collection<MilestoneStatus> statuses, LocalDateTime from, LocalDateTime to);
}
