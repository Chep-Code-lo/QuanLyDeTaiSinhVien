package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent, Long> {

    @EntityGraph(attributePaths = "period")
    List<AssessmentComponent> findByPeriodIdOrderByCodeAsc(Long periodId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select component from AssessmentComponent component where component.id = :id")
    Optional<AssessmentComponent> findByIdForUpdate(Long id);

    boolean existsByPeriodIdAndCodeIgnoreCase(Long periodId, String code);
}
