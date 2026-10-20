package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.ReportSubmission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportSubmissionRepository extends JpaRepository<ReportSubmission, Long> {
    List<ReportSubmission> findByMilestoneIdOrderByVersionNoDesc(Long milestoneId);
    long countByMilestoneId(Long milestoneId);
}
