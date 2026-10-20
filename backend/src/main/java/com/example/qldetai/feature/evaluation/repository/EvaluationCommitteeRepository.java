package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.EvaluationCommittee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvaluationCommitteeRepository
        extends JpaRepository<EvaluationCommittee, Long>, JpaSpecificationExecutor<EvaluationCommittee> {
    @org.springframework.data.jpa.repository.Modifying
    @Query("update EvaluationCommittee c set c.version = c.version + 1 where c.id = :id and c.version = :version")
    int advanceVersion(Long id, int version);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EvaluationCommittee c where c.id = :id")
    Optional<EvaluationCommittee> findForUpdate(Long id);
}
