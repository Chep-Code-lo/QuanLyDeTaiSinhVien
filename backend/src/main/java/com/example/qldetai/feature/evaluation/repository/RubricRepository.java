package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.Rubric;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface RubricRepository
        extends JpaRepository<Rubric, Long>, JpaSpecificationExecutor<Rubric> {
    @Override
    @EntityGraph(attributePaths = "component")
    List<Rubric> findAll(Specification<Rubric> specification, Sort sort);

    @EntityGraph(attributePaths = "component")
    List<Rubric> findByComponentIdOrderByVersionNoDesc(Long componentId);

    // Current read after the component lock, even with MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Rubric> findFirstByComponentIdOrderByVersionNoDesc(Long componentId);

    boolean existsByComponentIdAndStatus(Long componentId, String status);

    long countByComponentId(Long componentId);
}
