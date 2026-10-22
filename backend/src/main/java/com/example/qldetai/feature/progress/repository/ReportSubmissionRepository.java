package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.ReportSubmission;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportSubmissionRepository extends JpaRepository<ReportSubmission, Long> {
 List<ReportSubmission> findByMilestoneIdOrderByVersionNoDesc(Long milestoneId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"file", "submittedBy.user", "milestone"})
 List<ReportSubmission> findByMilestoneIdInOrderByVersionNoDesc(List<Long> milestoneIds);
 List<ReportSubmission> findByFileId(Long fileId);
 long countByMilestoneId(Long milestoneId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<ReportSubmission> findFirstByMilestoneIdOrderByVersionNoDesc(Long milestoneId);
}
