package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.ReportFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, Long> {
    List<ReportFeedback> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
}
