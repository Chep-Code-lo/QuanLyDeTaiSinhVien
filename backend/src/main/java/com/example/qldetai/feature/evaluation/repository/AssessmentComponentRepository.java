package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.AssessmentComponent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent, Long> {

    List<AssessmentComponent> findByPeriodIdOrderByCodeAsc(Long periodId);

    boolean existsByPeriodIdAndCodeIgnoreCase(Long periodId, String code);
}
