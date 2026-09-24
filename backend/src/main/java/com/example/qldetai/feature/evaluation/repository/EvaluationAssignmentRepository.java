package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvaluationAssignmentRepository
        extends JpaRepository<EvaluationAssignment, Long>, JpaSpecificationExecutor<EvaluationAssignment> {

    List<EvaluationAssignment> findByLecturerIdAndStatus(Long lecturerId, EvaluationStatus status);

    long countByAssignmentIdAndComponentIdAndStatus(Long assignmentId, Long componentId, EvaluationStatus status);
}
