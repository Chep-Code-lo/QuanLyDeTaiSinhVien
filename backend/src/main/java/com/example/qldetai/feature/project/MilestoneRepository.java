package com.example.qldetai.feature.project;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByStatusInAndDueAtBetween(
            Collection<MilestoneStatus> statuses,
            LocalDateTime from,
            LocalDateTime to);
}
