package com.example.qldetai.feature.project.repository;

import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    Page<ProjectAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    Page<ProjectAssignment> findByPeriodId(Long periodId, Pageable pageable);

    Page<ProjectAssignment> findByPeriodIdAndStatus(Long periodId, AssignmentStatus status, Pageable pageable);

    boolean existsByRegistrationId(Long registrationId);

    Optional<ProjectAssignment> findByRegistrationId(Long registrationId);

    boolean existsByGroupId(Long groupId);

    boolean existsByTopicId(Long topicId);

    java.util.Optional<ProjectAssignment> findByGroupId(Long groupId);
}
