package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.RubricCriterion;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {

    List<RubricCriterion> findByRubricIdOrderBySortOrder(Long rubricId);

    List<RubricCriterion> findByRubricIdInOrderBySortOrder(Collection<Long> rubricIds);
}
