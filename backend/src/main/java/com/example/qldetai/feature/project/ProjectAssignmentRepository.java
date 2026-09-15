package com.example.qldetai.feature.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    Page<ProjectAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    Page<ProjectAssignment> findByPeriodId(Long periodId, Pageable pageable);

    Page<ProjectAssignment> findByPeriodIdAndStatus(Long periodId, AssignmentStatus status, Pageable pageable);

    boolean existsByRegistrationId(Long registrationId);

    boolean existsByGroupId(Long groupId);

    boolean existsByTopicId(Long topicId);
}
