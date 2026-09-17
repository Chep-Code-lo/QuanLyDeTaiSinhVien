package com.example.qldetai.feature.progress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, Long> { List<ReportFeedback> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId); }
