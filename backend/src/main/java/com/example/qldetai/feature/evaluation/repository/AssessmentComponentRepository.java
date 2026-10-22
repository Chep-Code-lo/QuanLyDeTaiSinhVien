package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;

public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent, Long>, JpaSpecificationExecutor<AssessmentComponent> {
    @Override
    @EntityGraph(attributePaths = "period")
    List<AssessmentComponent> findAll(Specification<AssessmentComponent> specification,
            Sort sort);

    @EntityGraph(attributePaths = "period")
    List<AssessmentComponent> findByPeriodIdOrderByCodeAsc(Long periodId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select component from AssessmentComponent component where component.id = :id")
    Optional<AssessmentComponent> findByIdForUpdate(Long id);

    boolean existsByPeriodIdAndCodeIgnoreCase(Long periodId, String code);
}
