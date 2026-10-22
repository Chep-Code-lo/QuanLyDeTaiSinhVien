package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.ReportFeedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, Long> {
    List<ReportFeedback> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"lecturer.user", "submission"})
    List<ReportFeedback> findBySubmissionIdInOrderByCreatedAtAsc(List<Long> submissionIds);
}
