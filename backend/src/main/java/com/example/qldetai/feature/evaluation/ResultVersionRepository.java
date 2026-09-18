package com.example.qldetai.feature.evaluation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ResultVersionRepository
        extends JpaRepository<ResultVersion, Long>, JpaSpecificationExecutor<ResultVersion> {

    long countByAssignmentId(Long assignmentId);

    Optional<ResultVersion> findByAssignmentIdAndStatus(Long assignmentId, ResultStatus status);
}
