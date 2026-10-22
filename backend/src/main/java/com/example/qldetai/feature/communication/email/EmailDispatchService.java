package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.EmailQueuedResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.EmailRecipientResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.SendEmailRequest;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit mail dispatch, with recipient scope enforced independently of the UI. */
@Service
@RequiredArgsConstructor
public class EmailDispatchService {
    private static final Set<RoleCode> GROUP_ROLES = Set.of(RoleCode.STUDENT, RoleCode.LECTURER);
    private static final Sort RECIPIENT_ORDER = Sort.by("fullName").ascending().and(Sort.by("id"));
    private final UserRepository users;
    private final EmailNotificationSettingRepository settings;
    private final EmailOutboxService outbox;
    private final EmailNotificationProperties properties;
    private final LecturerRepository lecturers;
    private final EmailRecipientAccess recipientAccess;
    private final EmailTemplateService templates;

    @Transactional(readOnly = true)
    public PageResponse<EmailRecipientResponse> recipients(
            Long actorId, RoleCode role, String search, int page, int size) {
        Sender sender = requireSender(actorId);
        validateRole(role, sender);
        return PageResponse.from(
                users.findAll(
                                recipientAccess.accessible(sender.lecturerUserId(), role, search),
                                PageRequest.of(page, size, RECIPIENT_ORDER))
                        .map(
                                user ->
                                        new EmailRecipientResponse(
                                                user.getId(),
                                                user.getFullName(),
                                                user.getEmail())));
    }

    @Transactional
    public EmailQueuedResponse send(
            Long actorId, EmailNotificationEventType eventType, SendEmailRequest request) {
        Sender sender = requireSender(actorId);
        if (eventType == EmailNotificationEventType.RESULT_PUBLISHED) {
            throw new BusinessRuleException(
                    "Result emails are sent only when a result is published.");
        }
        EmailNotificationSetting setting =
                settings.findByEventTypeAndScopeTypeAndScopeId(
                                eventType,
                                sender.admin()
                                        ? NotificationScopeType.SYSTEM
                                        : NotificationScopeType.LECTURER,
                                sender.admin() ? 0L : actorId)
                        .orElseThrow(
                                () ->
                                        new BusinessRuleException(
                                                "Save this email setting before sending email."));
        return sendSetting(actorId, setting, request, sender);
    }

    @Transactional
    public EmailQueuedResponse sendTemplate(
            Long actorId, Long templateId, SendEmailRequest request) {
        Sender sender = requireSender(actorId);
        return sendSetting(
                actorId, templates.dispatchSetting(templateId, actorId), request, sender);
    }

    private EmailQueuedResponse sendSetting(
            Long actorId,
            EmailNotificationSetting setting,
            SendEmailRequest request,
            Sender sender) {
        Set<RoleCode> roles =
                request.recipientRoles() == null ? Set.of() : request.recipientRoles();
        Set<Long> ids = request.recipientUserIds() == null ? Set.of() : request.recipientUserIds();
        if (roles.isEmpty() && ids.isEmpty()) {
            throw new BusinessRuleException("Choose a recipient group or at least one person.");
        }
        if (!GROUP_ROLES.containsAll(roles)) {
            throw new BusinessRuleException(
                    "Only student and lecturer recipient groups are supported.");
        }
        roles.forEach(role -> validateRole(role, sender));
        List<User> selected =
                ids.isEmpty()
                        ? List.of()
                        : users.findAll(
                                recipientAccess
                                        .accessible(sender.lecturerUserId(), null, null)
                                        .and((root, query, cb) -> root.get("id").in(ids)));
        if (selected.size() != ids.size()
                || selected.stream()
                        .anyMatch(user -> user.getEmail() == null || user.getEmail().isBlank())) {
            throw new BusinessRuleException(
                    "One or more selected recipients are unavailable. Reload the recipient list.");
        }
        String eventKey = "email-manual:" + UUID.randomUUID();
        EmailEventContext context =
                new EmailEventContext(
                        setting.getEventType(),
                        eventKey,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        actorId,
                        List.of(),
                        List.of(),
                        Map.of("websiteUrl", properties.getBaseUrl()));
        Set<String> addresses = new HashSet<>();
        selected.forEach(recipient -> queue(setting, context, recipient, addresses));
        for (RoleCode role : roles) {
            int page = 0;
            Page<User> recipients;
            do {
                recipients =
                        users.findAll(
                                recipientAccess.accessible(sender.lecturerUserId(), role, null),
                                PageRequest.of(page++, 100, RECIPIENT_ORDER));
                recipients.forEach(recipient -> queue(setting, context, recipient, addresses));
            } while (recipients.hasNext());
        }
        if (addresses.isEmpty()) {
            throw new BusinessRuleException("No recipients matched the selected group.");
        }
        return new EmailQueuedResponse(addresses.size());
    }

    private void queue(
            EmailNotificationSetting setting,
            EmailEventContext context,
            User recipient,
            Set<String> addresses) {
        if (addresses.add(recipient.getEmail().trim().toLowerCase(Locale.ROOT))) {
            outbox.enqueueManual(setting, recipient, context);
        }
    }

    private void validateRole(RoleCode role, Sender sender) {
        if (role != null && !GROUP_ROLES.contains(role)) {
            throw new BusinessRuleException(
                    "Only student and lecturer recipient groups are supported.");
        }
        if (!sender.admin() && role == RoleCode.LECTURER) {
            throw new AccessDeniedException("Lecturers can email only their supervised students.");
        }
    }

    private Sender requireSender(Long actorId) {
        User actor =
                users.findWithRolesById(actorId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User account was not found."));
        boolean admin =
                actor.getUserRoles().stream()
                        .anyMatch(role -> role.getRole().getCode() == RoleCode.ADMIN);
        boolean lecturer =
                actor.getUserRoles().stream()
                        .anyMatch(role -> role.getRole().getCode() == RoleCode.LECTURER);
        if (actor.getStatus() != UserStatus.ACTIVE || (!admin && !lecturer)) {
            throw new AccessDeniedException("A lecturer or administrator account is required.");
        }
        if (admin) return new Sender(true, null);
        lecturers
                .findByUserId(actorId)
                .orElseThrow(() -> new AccessDeniedException("A lecturer profile is required."));
        return new Sender(false, actorId);
    }

    private record Sender(boolean admin, Long lecturerUserId) {}
}
