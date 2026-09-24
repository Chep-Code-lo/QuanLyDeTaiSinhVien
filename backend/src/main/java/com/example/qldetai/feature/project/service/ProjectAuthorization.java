package com.example.qldetai.feature.project.service;

import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.service.TopicAuthorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("projectAuthorization")
@Transactional(readOnly = true)
public class ProjectAuthorization {

    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final TopicAuthorization topicAuthorization;

    public ProjectAuthorization(
            ProjectAssignmentRepository projectAssignmentRepository,
            TopicAuthorization topicAuthorization) {
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.topicAuthorization = topicAuthorization;
    }

    public boolean canManageAssignment(Long assignmentId, Authentication authentication) {
        return projectAssignmentRepository
                .findById(assignmentId)
                .map(
                        assignment ->
                                topicAuthorization.canManageTopic(
                                        assignment.getTopic().getId(), authentication))
                .orElse(false);
    }
}
