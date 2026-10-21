package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.GradeChangeRequest;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GradeChangeRequestRepository
        extends JpaRepository<GradeChangeRequest, Long>, JpaSpecificationExecutor<GradeChangeRequest> {

    @Override
    @EntityGraph(attributePaths = {"sheet.evaluation", "requestedBy", "reviewedBy"})
    Page<GradeChangeRequest> findAll(Specification<GradeChangeRequest> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select change from GradeChangeRequest change where change.id = :id")
    Optional<GradeChangeRequest> findByIdForUpdate(Long id);

    boolean existsBySheetIdAndStatusIn(Long sheetId, Collection<GradeChangeStatus> statuses);

    Optional<GradeChangeRequest> findFirstBySheetIdAndStatusOrderByCreatedAtDesc(Long sheetId, GradeChangeStatus status);
}
