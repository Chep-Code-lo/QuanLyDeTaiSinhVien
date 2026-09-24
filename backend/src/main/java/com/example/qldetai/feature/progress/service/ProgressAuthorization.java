package com.example.qldetai.feature.progress.service;

import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import com.example.qldetai.feature.progress.repository.ReportSubmissionRepository;
import com.example.qldetai.feature.project.service.ProjectAuthorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("progressAuthorization")
@Transactional(readOnly = true)
public class ProgressAuthorization {

    private final MilestoneRepository milestoneRepository;
    private final ReportSubmissionRepository reportSubmissionRepository;
    private final ProjectAuthorization projectAuthorization;

    public ProgressAuthorization(
            MilestoneRepository milestoneRepository,
            ReportSubmissionRepository reportSubmissionRepository,
            ProjectAuthorization projectAuthorization) {
        this.milestoneRepository = milestoneRepository;
        this.reportSubmissionRepository = reportSubmissionRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public boolean canManageMilestone(Long milestoneId, Authentication authentication) {
        return milestoneRepository
                .findById(milestoneId)
                .map(
                        milestone ->
                                projectAuthorization.canManageAssignment(
                                        milestone.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageSubmission(Long submissionId, Authentication authentication) {
        return reportSubmissionRepository
                .findById(submissionId)
                .map(
                        submission ->
                                canManageMilestone(
                                        submission.getMilestone().getId(), authentication))
                .orElse(false);
    }
}
