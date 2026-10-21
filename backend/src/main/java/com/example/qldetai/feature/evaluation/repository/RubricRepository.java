package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.Rubric;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, Long> {

    @EntityGraph(attributePaths = "component")
    List<Rubric> findByComponentIdOrderByVersionNoDesc(Long componentId);

    // Current read after the component lock, even with MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Rubric> findFirstByComponentIdOrderByVersionNoDesc(Long componentId);

    boolean existsByComponentIdAndStatus(Long componentId, String status);

    long countByComponentId(Long componentId);
}
