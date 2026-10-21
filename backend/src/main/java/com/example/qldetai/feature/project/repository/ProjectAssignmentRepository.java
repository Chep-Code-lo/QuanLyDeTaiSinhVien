package com.example.qldetai.feature.project.repository;

import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    Page<ProjectAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    Page<ProjectAssignment> findByPeriodId(Long periodId, Pageable pageable);

    Page<ProjectAssignment> findByPeriodIdAndStatus(Long periodId, AssignmentStatus status, Pageable pageable);

    long countByStatus(AssignmentStatus status);

    @Query("""
            select assignment from ProjectAssignment assignment
            join GroupMember member on member.group.id = assignment.group.id
            where member.student.user.id = :userId and member.leftAt is null
            order by assignment.assignedAt desc, assignment.id desc
            """)
    List<ProjectAssignment> findByCurrentStudentUserId(@Param("userId") Long userId);

    boolean existsByRegistrationId(Long registrationId);

    Optional<ProjectAssignment> findByRegistrationId(Long registrationId);

    boolean existsByGroupId(Long groupId);

    boolean existsByTopicId(Long topicId);

    java.util.Optional<ProjectAssignment> findByGroupId(Long groupId);
}
