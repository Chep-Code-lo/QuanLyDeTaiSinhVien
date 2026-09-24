package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreSheetRepository extends JpaRepository<ScoreSheet, Long> {

    Optional<ScoreSheet> findByEvaluationId(Long evaluationId);

    List<ScoreSheet> findByEvaluation_Assignment_Id(Long assignmentId);
}
