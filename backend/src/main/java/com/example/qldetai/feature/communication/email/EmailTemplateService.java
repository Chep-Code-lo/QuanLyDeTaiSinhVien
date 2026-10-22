package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.PreviewNotificationSettingRequest;
import com.example.qldetai.feature.communication.email.EmailTemplateDtos.EmailTemplateResponse;
import com.example.qldetai.feature.communication.email.EmailTemplateDtos.SaveEmailTemplateRequest;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    private final EmailTemplateRepository templates;
    private final UserRepository users;
    private final EmailNotificationSettingsService settings;

    @Transactional(readOnly = true)
    public PageResponse<EmailTemplateResponse> list(Long actorId, int page, int size) {
        requireAuthor(actorId);
        return PageResponse.from(
                templates
                        .findByOwnerId(
                                actorId,
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("updatedAt")
                                                .descending()
                                                .and(Sort.by("id").descending())))
                        .map(this::response));
    }

    @Transactional
    public EmailTemplateResponse create(Long actorId, SaveEmailTemplateRequest request) {
        User actor = requireAuthor(actorId);
        EmailTemplate template = new EmailTemplate();
        template.setOwner(actor);
        apply(template, request, actor);
        return response(templates.saveAndFlush(template));
    }

    @Transactional
    public EmailTemplateResponse update(Long id, Long actorId, SaveEmailTemplateRequest request) {
        User actor = requireAuthor(actorId);
        EmailTemplate template = requireOwned(id, actorId);
        if (request.version() == null || template.getVersion() != request.version()) {
            throw new ConflictException("The email template was changed. Reload and try again.");
        }
        apply(template, request, actor);
        return response(templates.saveAndFlush(template));
    }

    @Transactional(readOnly = true)
    public EmailNotificationSetting dispatchSetting(Long id, Long actorId) {
        User actor = requireAuthor(actorId);
        EmailTemplate template = requireOwned(id, actorId);
        EmailNotificationSetting setting = new EmailNotificationSetting();
        setting.setEventType(EmailNotificationEventType.TASK_ASSIGNED);
        setting.setScopeType(
                isAdmin(actor) ? NotificationScopeType.SYSTEM : NotificationScopeType.LECTURER);
        setting.setScopeId(isAdmin(actor) ? 0L : actorId);
        setting.setSenderName(template.getSenderName());
        setting.setFromEmail(template.getFromEmail());
        setting.setReplyToEmail(template.getReplyToEmail());
        setting.setSubjectTemplate(template.getSubjectTemplate());
        setting.setBodyTemplate(template.getBodyTemplate());
        return setting;
    }

    private void apply(EmailTemplate template, SaveEmailTemplateRequest request, User actor) {
        // Reuse the existing sender allow-list, scope checks and plain-text token validation.
        var validated =
                settings.preview(
                        actor.getId(),
                        EmailNotificationEventType.TASK_ASSIGNED,
                        new PreviewNotificationSettingRequest(
                                isAdmin(actor)
                                        ? NotificationScopeType.SYSTEM
                                        : NotificationScopeType.LECTURER,
                                isAdmin(actor) ? 0L : actor.getId(),
                                request.senderName(),
                                request.fromEmail(),
                                request.replyToEmail(),
                                request.subjectTemplate(),
                                request.bodyTemplate(),
                                null,
                                null,
                                null));
        template.setName(request.name().trim());
        template.setSenderName(validated.senderName());
        template.setFromEmail(validated.fromEmail());
        template.setReplyToEmail(validated.replyToEmail());
        template.setSubjectTemplate(request.subjectTemplate().trim());
        template.setBodyTemplate(request.bodyTemplate().trim());
    }

    private EmailTemplate requireOwned(Long id, Long actorId) {
        return templates
                .findByIdAndOwnerId(id, actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Email template was not found."));
    }

    private User requireAuthor(Long id) {
        User actor =
                users.findWithRolesById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User account was not found."));
        if (actor.getStatus() != UserStatus.ACTIVE
                || actor.getUserRoles().stream()
                        .noneMatch(
                                role ->
                                        role.getRole().getCode() == RoleCode.ADMIN
                                                || role.getRole().getCode() == RoleCode.LECTURER)) {
            throw new AccessDeniedException("A lecturer or administrator account is required.");
        }
        return actor;
    }

    private boolean isAdmin(User actor) {
        return actor.getUserRoles().stream()
                .anyMatch(role -> role.getRole().getCode() == RoleCode.ADMIN);
    }

    private EmailTemplateResponse response(EmailTemplate template) {
        return new EmailTemplateResponse(
                template.getId(),
                template.getName(),
                template.getSenderName(),
                template.getFromEmail(),
                template.getReplyToEmail(),
                template.getSubjectTemplate(),
                template.getBodyTemplate(),
                template.getVersion(),
                template.getUpdatedAt());
    }
}
