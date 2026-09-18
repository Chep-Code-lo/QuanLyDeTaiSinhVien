package com.example.qldetai.feature.evaluation;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GradeChangeRequestRepository
        extends JpaRepository<GradeChangeRequest, Long>, JpaSpecificationExecutor<GradeChangeRequest> {

    boolean existsBySheetIdAndStatusIn(Long sheetId, Collection<GradeChangeStatus> statuses);

    Optional<GradeChangeRequest> findFirstBySheetIdAndStatusOrderByCreatedAtDesc(Long sheetId, GradeChangeStatus status);
}
