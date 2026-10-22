package com.example.qldetai.feature.project.repository;

import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    @Override
    @EntityGraph(attributePaths = {"period", "group", "topic", "assignedBy"})
    Page<ProjectAssignment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "assignedBy"})
    Page<ProjectAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "assignedBy"})
    Page<ProjectAssignment> findByPeriodId(Long periodId, Pageable pageable);

    @EntityGraph(attributePaths = {"period", "group", "topic", "assignedBy"})
    Page<ProjectAssignment> findByPeriodIdAndStatus(
            Long periodId, AssignmentStatus status, Pageable pageable);

    long countByStatus(AssignmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from ProjectAssignment assignment where assignment.id = :id")
    Optional<ProjectAssignment> findByIdForUpdate(@Param("id") Long id);

    @Query(
            """
            select assignment from ProjectAssignment assignment
            join GroupMember member on member.group.id = assignment.group.id
            where member.student.user.id = :userId and member.leftAt is null
            order by assignment.assignedAt desc, assignment.id desc
            """)
    List<ProjectAssignment> findByCurrentStudentUserId(@Param("userId") Long userId);

    boolean existsByRegistrationId(Long registrationId);

    Optional<ProjectAssignment> findByRegistrationId(Long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select assignment from ProjectAssignment assignment where assignment.topic.id = :topicId")
    Optional<ProjectAssignment> findByTopicIdForUpdate(Long topicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select assignment from ProjectAssignment assignment where assignment.group.id = :groupId")
    Optional<ProjectAssignment> findByGroupIdForUpdate(Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select assignment from ProjectAssignment assignment where assignment.registration.id = :registrationId")
    Optional<ProjectAssignment> findByRegistrationIdForUpdate(Long registrationId);

    boolean existsByGroupId(Long groupId);

    boolean existsByTopicId(Long topicId);

    java.util.Optional<ProjectAssignment> findByGroupId(Long groupId);
}
