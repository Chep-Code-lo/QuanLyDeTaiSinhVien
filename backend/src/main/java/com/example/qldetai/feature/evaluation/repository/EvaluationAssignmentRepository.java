package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EvaluationAssignmentRepository
        extends JpaRepository<EvaluationAssignment, Long>,
                JpaSpecificationExecutor<EvaluationAssignment> {

    @Override
    @EntityGraph(
            attributePaths = {
                "assignment.period",
                "assignment.group",
                "assignment.topic",
                "component",
                "rubric",
                "lecturer.user",
                "defense"
            })
    Page<EvaluationAssignment> findAll(
            Specification<EvaluationAssignment> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evaluation from EvaluationAssignment evaluation where evaluation.id = :id")
    Optional<EvaluationAssignment> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select evaluation from EvaluationAssignment evaluation
            where evaluation.assignment.id = :assignmentId and evaluation.component.id = :componentId
              and evaluation.status = com.example.qldetai.feature.evaluation.domain.EvaluationStatus.ACTIVE
            """)
    List<EvaluationAssignment> findActiveForUpdate(Long assignmentId, Long componentId);

    List<EvaluationAssignment> findByLecturerIdAndStatus(Long lecturerId, EvaluationStatus status);

    long countByAssignmentIdAndComponentIdAndStatus(
            Long assignmentId, Long componentId, EvaluationStatus status);

    List<EvaluationAssignment> findByAssignmentIdAndComponentIdAndStatus(
            Long assignmentId, Long componentId, EvaluationStatus status);

    List<EvaluationAssignment> findByAssignmentIdAndStatus(
            Long assignmentId, EvaluationStatus status);
}
