package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.Milestone;
import com.example.qldetai.feature.progress.domain.MilestoneStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByAssignmentIdOrderByDueAtAsc(Long assignmentId);

    List<Milestone> findByStatusInAndDueAtBetween(
            Collection<MilestoneStatus> statuses,
            LocalDateTime from,
            LocalDateTime to);
}
