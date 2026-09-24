package com.example.qldetai.feature.groupregistration.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.GroupMember;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.ReviewTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.TopicRegistrationResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.WithdrawTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.domain.VisibilityType;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final TopicRegistrationRepository topicRegistrationRepository;
    private final TopicRepository topicRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public RegistrationService(
            TopicRegistrationRepository topicRegistrationRepository,
            TopicRepository topicRepository,
            StudentGroupRepository studentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.topicRepository = topicRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TopicRegistrationResponse> list(Long periodId, RegistrationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("registeredAt").descending());
        Page<TopicRegistration> registrations;
        if (periodId != null && status != null) {
            registrations = topicRegistrationRepository.findByPeriodIdAndStatus(periodId, status, pageable);
        } else if (periodId != null) {
            registrations = topicRegistrationRepository.findByPeriodId(periodId, pageable);
        } else if (status != null) {
            registrations = topicRegistrationRepository.findByStatus(status, pageable);
        } else {
            registrations = topicRegistrationRepository.findAll(pageable);
        }
        return PageResponse.from(registrations.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public TopicRegistrationResponse get(Long id) {
        return toResponse(requireRegistration(id));
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_REGISTRATION_CREATE", entityType = "TOPIC_REGISTRATION")
    public TopicRegistrationResponse create(CreateTopicRegistrationRequest request, Long actorId) {
        Student actor = requireStudent(actorId);
        StudentGroup group = requireGroup(request.groupId());
        Topic topic = requireTopic(request.topicId());
        ensureLeader(group.getId(), actor.getId());
        ensureGroupReady(group);
        if (projectAssignmentRepository.existsByGroupId(group.getId())) {
            throw new BusinessRuleException("This group already has a project assignment.");
        }
        if (topicRegistrationRepository.existsByGroupIdAndStatusIn(
                group.getId(), List.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED))) {
            throw new ConflictException("This group already has a pending or approved topic registration.");
        }
        if (!topic.getPeriod().getId().equals(group.getPeriod().getId())) {
            throw new BusinessRuleException("Topic and student group must belong to the same registration period.");
        }
        if (topic.getStatus() != TopicStatus.PUBLISHED) {
            throw new BusinessRuleException("Only published topics can be registered.");
        }
        if (topic.getVisibilityType() == VisibilityType.RESERVED
                && (topic.getReservedGroup() == null || !topic.getReservedGroup().getId().equals(group.getId()))) {
            throw new BusinessRuleException("This topic is reserved for another student group.");
        }

        TopicRegistration registration = new TopicRegistration();
        registration.setPeriod(group.getPeriod());
        registration.setGroup(group);
        registration.setTopic(topic);
        registration.setRegisteredBy(actor);
        registration.setMotivation(normalizeNullable(request.motivation()));
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setRegisteredAt(LocalDateTime.now(clock));
        return toResponse(topicRegistrationRepository.save(registration));
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_REGISTRATION_REVIEW", entityType = "TOPIC_REGISTRATION", entityIdArgument = 0)
    public TopicRegistrationResponse review(Long id, ReviewTopicRegistrationRequest request, Long actorId) {
        TopicRegistration registration = requireRegistration(id);
        User actor = requireReviewer(actorId);
        verifyVersion(registration, request.version());
        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException("Only pending registrations can be reviewed.");
        }
        if (request.status() != RegistrationStatus.APPROVED && request.status() != RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Registration review must end with APPROVED or REJECTED.");
        }
        if (request.status() == RegistrationStatus.REJECTED
                && (request.decisionReason() == null || request.decisionReason().isBlank())) {
            throw new BusinessRuleException("A rejection reason is required.");
        }

        registration.setStatus(request.status());
        registration.setReviewedBy(actor);
        registration.setReviewedAt(LocalDateTime.now(clock));
        registration.setDecisionReason(normalizeNullable(request.decisionReason()));
        notifyGroup(registration, request.status() == RegistrationStatus.APPROVED
                ? "Đăng ký đề tài đã được duyệt."
                : "Đăng ký đề tài đã bị từ chối.");
        return toResponse(registration);
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_REGISTRATION_WITHDRAW", entityType = "TOPIC_REGISTRATION", entityIdArgument = 0)
    public TopicRegistrationResponse withdraw(Long id, WithdrawTopicRegistrationRequest request, Long actorId) {
        TopicRegistration registration = requireRegistration(id);
        Student actor = requireStudent(actorId);
        verifyVersion(registration, request.version());
        ensureLeader(registration.getGroup().getId(), actor.getId());
        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException("Only pending registrations can be withdrawn.");
        }
        registration.setStatus(RegistrationStatus.WITHDRAWN);
        registration.setDecisionReason(request.decisionReason().trim());
        return toResponse(registration);
    }

    private void ensureGroupReady(StudentGroup group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new BusinessRuleException("Only active student groups can register a topic.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(group.getPeriod().getRegistrationStartAt()) || now.isAfter(group.getPeriod().getRegistrationEndAt())) {
            throw new BusinessRuleException("Topics can only be registered during the registration window.");
        }
    }

    private void ensureLeader(Long groupId, Long studentId) {
        GroupMember membership = groupMemberRepository.findByGroupIdAndStudentIdAndLeftAtIsNull(groupId, studentId)
                .orElseThrow(() -> new AccessDeniedException("Only active group members can manage topic registration."));
        if (membership.getMemberRole() != GroupMemberRole.LEADER) {
            throw new AccessDeniedException("Only the group leader can manage topic registration.");
        }
    }

    private Student requireStudent(Long actorId) {
        User actor = userRepository.findWithRolesById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        if (actor.getUserRoles().stream().noneMatch(userRole -> userRole.getRole().getCode() == RoleCode.STUDENT)) {
            throw new AccessDeniedException("A student account is required for this action.");
        }
        return studentRepository.findByUserId(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile was not found."));
    }

    private User requireReviewer(Long actorId) {
        User actor = userRepository.findWithRolesById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        boolean allowed = actor.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getCode())
                .anyMatch(role -> role == RoleCode.ADMIN || role == RoleCode.LECTURER);
        if (!allowed) {
            throw new AccessDeniedException("Only lecturers or administrators can review topic registrations.");
        }
        return actor;
    }

    private StudentGroup requireGroup(Long id) {
        return studentGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student group was not found."));
    }

    private Topic requireTopic(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic was not found."));
    }

    private TopicRegistration requireRegistration(Long id) {
        return topicRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic registration was not found."));
    }

    private void verifyVersion(TopicRegistration registration, int requestedVersion) {
        if (registration.getVersion() != requestedVersion) {
            throw new ConflictException("The topic registration was changed by another user. Reload and try again.");
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notifyGroup(TopicRegistration registration, String message) {
        groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(registration.getGroup().getId())
                .forEach(member -> notificationService.publish(
                        member.getStudent().getUser().getId(),
                        "topic-registration-" + registration.getId() + "-" + registration.getStatus() + "-" + member.getStudent().getId(),
                        "Kết quả đăng ký đề tài",
                        message,
                        "/topic-registrations/" + registration.getId()));
    }

    private TopicRegistrationResponse toResponse(TopicRegistration registration) {
        return new TopicRegistrationResponse(
                registration.getId(),
                registration.getPeriod().getId(),
                registration.getPeriod().getName(),
                registration.getGroup().getId(),
                registration.getGroup().getName(),
                registration.getTopic().getId(),
                registration.getTopic().getCode(),
                registration.getTopic().getTitle(),
                registration.getRegisteredBy().getId(),
                registration.getRegisteredBy().getUser().getFullName(),
                registration.getMotivation(),
                registration.getStatus(),
                registration.getRegisteredAt(),
                registration.getReviewedBy() == null ? null : registration.getReviewedBy().getId(),
                registration.getReviewedBy() == null ? null : registration.getReviewedBy().getFullName(),
                registration.getReviewedAt(),
                registration.getDecisionReason(),
                registration.getVersion());
    }
}
