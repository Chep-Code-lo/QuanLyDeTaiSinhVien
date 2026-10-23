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
    private final com.example.qldetai.feature.academic.service.AcademicAuthorization academicAuthorization;
    private final com.example.qldetai.feature.topic.repository.TopicSupervisorRepository supervisors;
    private final com.example.qldetai.feature.academic.service.AcademicReadScope readScope;

    public ProjectAuthorization(
            ProjectAssignmentRepository projectAssignmentRepository,
            TopicAuthorization topicAuthorization,
            com.example.qldetai.feature.academic.service.AcademicAuthorization academicAuthorization,
            com.example.qldetai.feature.topic.repository.TopicSupervisorRepository supervisors,
            com.example.qldetai.feature.academic.service.AcademicReadScope readScope) {
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.topicAuthorization = topicAuthorization;
        this.academicAuthorization = academicAuthorization;
        this.supervisors = supervisors;
        this.readScope = readScope;
    }

    public boolean canReadAssignment(Long assignmentId, Long actorId) {
        return projectAssignmentRepository.exists(readScope.projects(actorId)
                .and((r, q, cb) -> cb.equal(r.get("id"), assignmentId)));
    }

    public boolean canManageAssignment(Long assignmentId, Long actorId) {
        return projectAssignmentRepository.findById(assignmentId)
                .map(a -> academicAuthorization.canManageDepartment(a.getTopic().getDepartment().getId(), actorId)).orElse(false);
    }

    public boolean canSuperviseAssignment(Long assignmentId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof com.example.qldetai.security.UserPrincipal p)
                || !p.roles().contains(com.example.qldetai.feature.identity.domain.RoleCode.LECTURER)) return false;
        return projectAssignmentRepository.findById(assignmentId).map(a ->
                supervisors.findByTopicIdOrderBySlotNoAsc(a.getTopic().getId()).stream()
                        .anyMatch(s -> s.getLecturer().getUser().getId().equals(p.id()))).orElse(false);
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
