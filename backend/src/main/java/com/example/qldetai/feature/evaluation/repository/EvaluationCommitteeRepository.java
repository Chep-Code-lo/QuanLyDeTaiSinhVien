package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.EvaluationCommittee;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EvaluationCommitteeRepository
        extends JpaRepository<EvaluationCommittee, Long>,
                JpaSpecificationExecutor<EvaluationCommittee> {

    @Override
    @EntityGraph(attributePaths = {"period", "faculty", "createdBy"})
    Page<EvaluationCommittee> findAll(
            Specification<EvaluationCommittee> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select committee from EvaluationCommittee committee where committee.id = :id")
    Optional<EvaluationCommittee> findByIdForUpdate(Long id);

    @Modifying
    @Query(
            "update EvaluationCommittee committee set committee.version = committee.version + 1 where committee.id = :id and committee.version = :version")
    int advanceVersion(Long id, int version);
}
