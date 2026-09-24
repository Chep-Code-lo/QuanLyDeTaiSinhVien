package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.Rubric;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, Long> {

    List<Rubric> findByComponentIdOrderByVersionNoDesc(Long componentId);

    long countByComponentId(Long componentId);
}
