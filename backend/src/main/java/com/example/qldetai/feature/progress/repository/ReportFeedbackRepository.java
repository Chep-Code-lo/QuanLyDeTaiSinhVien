package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.ReportFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, Long> {
 List<ReportFeedback> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"lecturer.user", "submission"})
 List<ReportFeedback> findBySubmissionIdInOrderByCreatedAtAsc(List<Long> submissionIds);
}
