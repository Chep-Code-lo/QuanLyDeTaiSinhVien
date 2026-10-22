package com.example.qldetai.feature.project.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.email.EmailNotificationEventService;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.dto.ProjectDtos.CreateAssignmentRequest;
import com.example.qldetai.feature.project.dto.ProjectDtos.ProjectAssignmentResponse;
import com.example.qldetai.feature.project.dto.ProjectDtos.UpdateAssignmentStatusRequest;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectAssignmentService {

    private static final Map<AssignmentStatus, EnumSet<AssignmentStatus>> TRANSITIONS =
            Map.of(
                    AssignmentStatus.IN_PROGRESS,
                            EnumSet.of(AssignmentStatus.COMPLETED, AssignmentStatus.CANCELLED),
                    AssignmentStatus.COMPLETED, EnumSet.noneOf(AssignmentStatus.class),
                    AssignmentStatus.CANCELLED, EnumSet.noneOf(AssignmentStatus.class));

    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final TopicRepository topicRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailNotificationEventService emailNotificationEventService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ProjectAssignmentResponse> list(
            Long periodId, AssignmentStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("assignedAt").descending());
        Page<ProjectAssignment> assignments;
        if (periodId != null && status != null) {
            assignments =
                    projectAssignmentRepository.findByPeriodIdAndStatus(periodId, status, pageable);
        } else if (periodId != null) {
            assignments = projectAssignmentRepository.findByPeriodId(periodId, pageable);
        } else if (status != null) {
            assignments = projectAssignmentRepository.findByStatus(status, pageable);
        } else {
            assignments = projectAssignmentRepository.findAll(pageable);
        }
        return PageResponse.from(assignments.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ProjectAssignmentResponse> listForStudent(Long userId) {
        return projectAssignmentRepository.findByCurrentStudentUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectAssignmentResponse get(Long id) {
        return toResponse(requireAssignment(id));
    }

    @Transactional
    @BusinessAudit(action = "PROJECT_ASSIGNMENT_CREATE", entityType = "PROJECT_ASSIGNMENT")
    public ProjectAssignmentResponse create(CreateAssignmentRequest request, Long actorId) {
        User actor = requireManager(actorId);
        TopicRegistration registration = requireRegistration(request.registrationId());
        return createForApprovedRegistration(registration, actor);
    }

    /**
     * Completes the approval workflow.  It deliberately joins the caller's transaction so an
     * approved registration can never be committed without its project assignment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectAssignmentResponse createForApprovedRegistration(
            TopicRegistration registration, User actor) {
        if (registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new BusinessRuleException(
                    "Only approved topic registrations can become project assignments.");
        }
        // The group and topic are the shared roots for registration/member changes and competing
        // approvals.
        studentGroupRepository.findByIdForUpdate(registration.getGroup().getId()).orElseThrow();
        topicRepository.findByIdForUpdate(registration.getTopic().getId()).orElseThrow();
        ProjectAssignment existing =
                projectAssignmentRepository
                        .findByRegistrationIdForUpdate(registration.getId())
                        .orElse(null);
        if (existing != null) {
            // The endpoint used to be a separate manual step. Keeping retries idempotent avoids
            // breaking clients which still issue that old follow-up request.
            return toResponse(existing);
        }
        if (projectAssignmentRepository
                .findByGroupIdForUpdate(registration.getGroup().getId())
                .isPresent()) {
            throw new ConflictException("This student group already has a project assignment.");
        }
        if (projectAssignmentRepository
                .findByTopicIdForUpdate(registration.getTopic().getId())
                .isPresent()) {
            throw new ConflictException("This topic already has a project assignment.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setRegistration(registration);
        assignment.setGroup(registration.getGroup());
        assignment.setTopic(registration.getTopic());
        assignment.setPeriod(registration.getPeriod());
        assignment.setAssignedBy(actor);
        assignment.setAssignedAt(now);
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment = projectAssignmentRepository.save(assignment);

        registration.getGroup().setStatus(GroupStatus.LOCKED);
        topicRegistrationRepository
                .findByTopicIdAndStatus(registration.getTopic().getId(), RegistrationStatus.PENDING)
                .stream()
                .filter(candidate -> !candidate.getId().equals(registration.getId()))
                .forEach(
                        candidate -> {
                            candidate.setStatus(RegistrationStatus.REJECTED);
                            candidate.setReviewedBy(actor);
                            candidate.setReviewedAt(now);
                            candidate.setDecisionReason(
                                    "Topic has already been assigned to another group.");
                        });

        notifyGroup(assignment, "Đề tài đã được giao cho nhóm.");
        emailNotificationEventService.taskAssigned(assignment, actor);
        return toResponse(assignment);
    }

    @Transactional
    @BusinessAudit(
            action = "PROJECT_ASSIGNMENT_STATUS_UPDATE",
            entityType = "PROJECT_ASSIGNMENT",
            entityIdArgument = 0)
    public ProjectAssignmentResponse updateStatus(
            Long id, UpdateAssignmentStatusRequest request, Long actorId) {
        ProjectAssignment assignment = requireAssignment(id);
        requireManager(actorId);
        verifyVersion(assignment, request.version());
        if (!TRANSITIONS.get(assignment.getStatus()).contains(request.status())) {
            throw new BusinessRuleException(
                    "Project assignment cannot change from "
                            + assignment.getStatus()
                            + " to "
                            + request.status()
                            + ".");
        }

        assignment.setStatus(request.status());
        if (request.status() == AssignmentStatus.COMPLETED) {
            assignment.getGroup().setStatus(GroupStatus.CLOSED);
        } else if (request.status() == AssignmentStatus.CANCELLED) {
            long activeMembers =
                    groupMemberRepository.countByGroupIdAndLeftAtIsNull(
                            assignment.getGroup().getId());
            assignment
                    .getGroup()
                    .setStatus(
                            activeMembers >= assignment.getPeriod().getMinGroupMembers()
                                    ? GroupStatus.ACTIVE
                                    : GroupStatus.FORMING);
        }

        notifyGroup(
                assignment,
                request.status() == AssignmentStatus.COMPLETED
                        ? "Đề tài đã được đánh dấu hoàn thành."
                        : "Quyết định giao đề tài đã bị hủy.");
        return toResponse(assignment);
    }

    private TopicRegistration requireRegistration(Long id) {
        return topicRegistrationRepository
                .findByIdForUpdate(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Topic registration was not found."));
    }

    private ProjectAssignment requireAssignment(Long id) {
        return projectAssignmentRepository
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Project assignment was not found."));
    }

    private User requireManager(Long actorId) {
        User actor =
                userRepository
                        .findWithRolesById(actorId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User account was not found."));
        boolean allowed =
                actor.getUserRoles().stream()
                        .map(userRole -> userRole.getRole().getCode())
                        .anyMatch(role -> role == RoleCode.ADMIN || role == RoleCode.LECTURER);
        if (!allowed) {
            throw new AccessDeniedException(
                    "Only lecturers or administrators can manage project assignments.");
        }
        return actor;
    }

    private void verifyVersion(ProjectAssignment assignment, int requestedVersion) {
        if (assignment.getVersion() != requestedVersion) {
            throw new ConflictException(
                    "The project assignment was changed by another user. Reload and try again.");
        }
    }

    private void notifyGroup(ProjectAssignment assignment, String message) {
        groupMemberRepository
                .findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(assignment.getGroup().getId())
                .forEach(
                        member ->
                                notificationService.publish(
                                        member.getStudent().getUser().getId(),
                                        "project-assignment-"
                                                + assignment.getId()
                                                + "-"
                                                + assignment.getStatus()
                                                + "-"
                                                + member.getStudent().getId(),
                                        "Cập nhật đề tài được giao",
                                        message,
                                        "/project-assignments/" + assignment.getId()));
    }

    private ProjectAssignmentResponse toResponse(ProjectAssignment assignment) {
        return new ProjectAssignmentResponse(
                assignment.getId(),
                assignment.getRegistration().getId(),
                assignment.getGroup().getId(),
                assignment.getGroup().getName(),
                assignment.getTopic().getId(),
                assignment.getTopic().getCode(),
                assignment.getTopic().getTitle(),
                assignment.getPeriod().getId(),
                assignment.getPeriod().getName(),
                assignment.getAssignedBy().getId(),
                assignment.getAssignedBy().getFullName(),
                assignment.getAssignedAt(),
                assignment.getStatus(),
                assignment.getVersion());
    }
}
