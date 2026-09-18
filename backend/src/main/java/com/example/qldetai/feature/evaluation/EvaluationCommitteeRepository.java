package com.example.qldetai.feature.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvaluationCommitteeRepository
        extends JpaRepository<EvaluationCommittee, Long>, JpaSpecificationExecutor<EvaluationCommittee> {
}
