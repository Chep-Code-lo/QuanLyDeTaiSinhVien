package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreSheetRepository extends JpaRepository<ScoreSheet, Long> {

    Optional<ScoreSheet> findByEvaluationId(Long evaluationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sheet from ScoreSheet sheet where sheet.evaluation.id = :evaluationId")
    Optional<ScoreSheet> findByEvaluationIdForUpdate(Long evaluationId);

    List<ScoreSheet> findByEvaluationIdIn(Collection<Long> evaluationIds);

    List<ScoreSheet> findByEvaluation_Assignment_Id(Long assignmentId);
}
