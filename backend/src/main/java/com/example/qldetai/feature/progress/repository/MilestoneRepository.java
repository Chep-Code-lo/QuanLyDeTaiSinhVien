package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.Milestone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MilestoneRepository extends JpaRepository<Milestone, Long> { List<Milestone> findByAssignmentIdOrderByDueAtAsc(Long assignmentId); }
