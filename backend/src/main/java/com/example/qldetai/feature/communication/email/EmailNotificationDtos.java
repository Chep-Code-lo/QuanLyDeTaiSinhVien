package com.example.qldetai.feature.communication.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmailNotificationDtos {

    private EmailNotificationDtos() {
    }

    public record UpdateNotificationSettingRequest(
            NotificationScopeType scopeType,
            Long scopeId,
            @NotNull Boolean enabled,
            @NotBlank @Size(max = 150) String senderName,
            @Email @Size(max = 254) String fromEmail,
            @Email @Size(max = 254) String replyToEmail,
            @NotBlank @Size(max = 500) String subjectTemplate,
            @NotBlank @Size(max = 30000) String bodyTemplate,
            @NotEmpty Set<EmailRecipientGroup> recipientGroups,
            Integer reminderHours) {
    }

    /**
     * Template fields are optional so callers can preview the saved template or
     * an unsaved edit without persisting it first.
     */
    public record PreviewNotificationSettingRequest(
            NotificationScopeType scopeType,
            Long scopeId,
            @Size(max = 150) String senderName,
            @Email @Size(max = 254) String fromEmail,
            @Email @Size(max = 254) String replyToEmail,
            @Size(max = 500) String subjectTemplate,
            @Size(max = 30000) String bodyTemplate,
            EmailNotificationReferenceType referenceType,
            Long eventReferenceId,
            Map<String, String> sampleData) {
    }

    /** Test messages are always sent only to the authenticated user's account. */
    public record SendTestEmailRequest(
            NotificationScopeType scopeType,
            Long scopeId,
            EmailNotificationReferenceType referenceType,
            Long eventReferenceId) {
    }

    public record EmailNotificationSettingResponse(
            Long id,
            EmailNotificationEventType eventType,
            boolean enabled,
            String senderName,
            String fromEmail,
            String replyToEmail,
            String subjectTemplate,
            String bodyTemplate,
            Set<EmailRecipientGroup> recipientGroups,
            Integer reminderHours,
            NotificationScopeType scopeType,
            Long scopeId,
            Long updatedBy,
            String updatedByName,
            LocalDateTime updatedAt,
            int version,
            List<String> allowedVariables) {
    }

    public record EmailPreviewResponse(
            EmailNotificationEventType eventType,
            String senderName,
            String fromEmail,
            String replyToEmail,
            String subject,
            String body,
            List<String> allowedVariables,
            boolean sampleDataUsed) {
    }

    public record EmailTestQueuedResponse(Long outboxId, String recipientEmail, EmailOutboxStatus status) {
    }

    public record RecipientCandidateResponse(Long userId, String fullName, String email, String category) {
    }

    public record RecipientCandidatesResponse(List<RecipientCandidateResponse> recipients) {
    }

    public record DeliveryAttemptResponse(
            Long id,
            int attemptNo,
            EmailOutboxStatus status,
            String errorMessage,
            LocalDateTime createdAt) {
    }

    public record EmailLogResponse(
            Long id,
            EmailNotificationEventType eventType,
            String eventKey,
            String recipientName,
            String recipientEmail,
            String subject,
            EmailOutboxStatus status,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            LocalDateTime sentAt,
            String lastError,
            Long facultyId,
            Long relatedTopicId,
            Long relatedGroupId,
            Long relatedAssignmentId,
            LocalDateTime createdAt) {
    }

    public record EmailLogDetailResponse(EmailLogResponse log, List<DeliveryAttemptResponse> attempts) {
    }
}
