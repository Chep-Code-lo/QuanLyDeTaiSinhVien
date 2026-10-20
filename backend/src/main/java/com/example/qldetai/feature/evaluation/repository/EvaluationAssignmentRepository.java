package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvaluationAssignmentRepository
        extends JpaRepository<EvaluationAssignment, Long>, JpaSpecificationExecutor<EvaluationAssignment> {

    boolean existsByDefenseCommitteeId(Long committeeId);

    List<EvaluationAssignment> findByLecturerIdAndStatus(Long lecturerId, EvaluationStatus status);

    long countByAssignmentIdAndComponentIdAndStatus(Long assignmentId, Long componentId, EvaluationStatus status);

    List<EvaluationAssignment> findByAssignmentIdAndComponentIdAndStatus(
            Long assignmentId, Long componentId, EvaluationStatus status);

    List<EvaluationAssignment> findByAssignmentIdAndStatus(Long assignmentId, EvaluationStatus status);
}
