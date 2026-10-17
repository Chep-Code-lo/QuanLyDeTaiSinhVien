package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.DeliveryAttemptResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailLogDetailResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailLogResponse;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
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
public class EmailLogService {

    private final EmailOutboxRepository outboxRepository;
    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;
    private final Clock clock;

    public EmailLogService(
            EmailOutboxRepository outboxRepository,
            EmailDeliveryLogRepository deliveryLogRepository,
            UserRepository userRepository,
            LecturerRepository lecturerRepository,
            AcademicAppointmentRepository academicAppointmentRepository,
            TopicSupervisorRepository topicSupervisorRepository,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.userRepository = userRepository;
        this.lecturerRepository = lecturerRepository;
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.topicSupervisorRepository = topicSupervisorRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailLogResponse> list(Long actorId, int page, int size) {
        User actor = requireAuthorizedActor(actorId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<EmailOutbox> results;
        if (hasRole(actor, RoleCode.ADMIN)) {
            results = outboxRepository.findAll(pageable);
        } else {
            List<Long> facultyIds = academicAppointmentRepository.findActiveDeanFacultyIds(
                    actorId, LocalDateTime.now(clock));
            if (!facultyIds.isEmpty()) {
                results = outboxRepository.findAccessibleToDean(facultyIds, pageable);
            } else if (lecturerRepository.findByUserId(actorId).isPresent()) {
                results = outboxRepository.findAccessibleToLecturer(actorId, pageable);
            } else {
                throw new AccessDeniedException("A lecturer, dean, or administrator account is required.");
            }
        }
        return PageResponse.from(results.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public EmailLogDetailResponse get(Long actorId, Long outboxId) {
        User actor = requireAuthorizedActor(actorId);
        EmailOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Email log was not found."));
        if (!canAccess(actor, outbox)) {
            throw new AccessDeniedException("You do not have permission to view this email log.");
        }
        List<DeliveryAttemptResponse> attempts = deliveryLogRepository.findByOutboxIdOrderByCreatedAtAsc(outboxId).stream()
                .map(log -> new DeliveryAttemptResponse(
                        log.getId(), log.getAttemptNo(), log.getStatus(), log.getErrorMessage(), log.getCreatedAt()))
                .toList();
        return new EmailLogDetailResponse(toResponse(outbox), attempts);
    }

    private boolean canAccess(User actor, EmailOutbox outbox) {
        if (hasRole(actor, RoleCode.ADMIN)) {
            return true;
        }
        List<Long> facultyIds = academicAppointmentRepository.findActiveDeanFacultyIds(
                actor.getId(), LocalDateTime.now(clock));
        if (!facultyIds.isEmpty() && outbox.getFacultyId() != null && facultyIds.contains(outbox.getFacultyId())) {
            return true;
        }
        if (!facultyIds.isEmpty()
                && outbox.getResolvedScopeType() == NotificationScopeType.FACULTY
                && facultyIds.contains(outbox.getResolvedScopeId())) {
            return true;
        }
        if (lecturerRepository.findByUserId(actor.getId()).isEmpty()) {
            return false;
        }
        if (outbox.getResolvedScopeType() == NotificationScopeType.LECTURER
                && actor.getId().equals(outbox.getResolvedScopeId())) {
            return true;
        }
        return outbox.getRelatedTopicId() != null
                && topicSupervisorRepository.existsByTopicIdAndLecturerUserId(outbox.getRelatedTopicId(), actor.getId());
    }

    private User requireAuthorizedActor(Long actorId) {
        User actor = userRepository.findWithRolesById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        if (actor.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("An active user account is required.");
        }
        return actor;
    }

    private boolean hasRole(User user, RoleCode role) {
        return user.getUserRoles().stream().anyMatch(userRole -> userRole.getRole().getCode() == role);
    }

    private EmailLogResponse toResponse(EmailOutbox outbox) {
        return new EmailLogResponse(
                outbox.getId(),
                outbox.getEventType(),
                outbox.getEventKey(),
                outbox.getRecipientName(),
                outbox.getRecipientEmail(),
                outbox.getSubject(),
                outbox.getStatus(),
                outbox.getAttemptCount(),
                outbox.getNextAttemptAt(),
                outbox.getSentAt(),
                outbox.getLastError(),
                outbox.getFacultyId(),
                outbox.getRelatedTopicId(),
                outbox.getRelatedGroupId(),
                outbox.getRelatedAssignmentId(),
                outbox.getCreatedAt());
    }
}
