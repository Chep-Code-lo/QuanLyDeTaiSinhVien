package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ResultStatus;
import com.example.qldetai.feature.evaluation.domain.ResultVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResultVersionRepository
        extends JpaRepository<ResultVersion, Long>, JpaSpecificationExecutor<ResultVersion> {

    @Override
    @EntityGraph(attributePaths = {"assignment.period", "assignment.group", "assignment.topic", "finalizedBy", "publishedBy"})
    Page<ResultVersion> findAll(Specification<ResultVersion> specification, Pageable pageable);

    long countByAssignmentId(Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from ResultVersion result where result.assignment.id = :assignmentId")
    List<ResultVersion> findByAssignmentIdForUpdate(Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from ResultVersion result where result.id = :id")
    Optional<ResultVersion> findByIdForUpdate(Long id);

    Optional<ResultVersion> findByAssignmentIdAndStatus(Long assignmentId, ResultStatus status);

    @Query("""
            select resultVersion from ResultVersion resultVersion
            join ResultMember member on member.result.id = resultVersion.id
            where member.student.user.id = :userId
              and resultVersion.status = 'PUBLISHED'
            order by resultVersion.publishedAt desc, resultVersion.id desc
            """)
    List<ResultVersion> findPublishedByStudentUserId(@Param("userId") Long userId);
}
