package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ResultStatus;
import com.example.qldetai.feature.evaluation.domain.ResultVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultVersionRepository
        extends JpaRepository<ResultVersion, Long>, JpaSpecificationExecutor<ResultVersion> {

    long countByAssignmentId(Long assignmentId);

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
