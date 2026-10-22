package com.example.qldetai.feature.communication.email;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.mail.internet.InternetAddress;

/** Persists immutable delivery snapshots and performs SMTP work outside business transactions. */
@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final EmailOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final EmailTemplateRenderer templateRenderer;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final EmailOutboxDeliveryStateService deliveryStateService;
    private final EmailNotificationProperties properties;
    private final Clock clock;


    /** Called while the source business transaction is still active. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = RuntimeException.class)
    public void enqueue(
            EmailNotificationSetting setting,
            EmailEventContext context,
            List<EmailRecipientResolver.ResolvedRecipient> recipients) {
        if (!setting.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (EmailRecipientResolver.ResolvedRecipient resolvedRecipient : recipients) {
            User recipient = resolvedRecipient.user();
            if (recipient.getStatus() != UserStatus.ACTIVE
                    || recipient.getEmail() == null
                    || recipient.getEmail().isBlank()) {
                continue;
            }
            Map<String, String> variables = new LinkedHashMap<>(context.variables());
            variables.put("studentName", recipient.getFullName());
            EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(
                    context.eventType(), setting.getSubjectTemplate(), setting.getBodyTemplate(), variables);
            EmailOutbox outbox = newOutbox(setting, context, recipient, rendered, context.eventKey(), now);
            insertIfAbsent(outbox, now);
        }
    }

    @Transactional
    public EmailOutbox enqueueTest(
            EmailNotificationSetting setting,
            User recipient,
            EmailEventContext context) {
        if (recipient.getStatus() != UserStatus.ACTIVE || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            throw new BusinessRuleException("The current account does not have an active email address for testing.");
        }
        Map<String, String> variables = new LinkedHashMap<>(context.variables());
        variables.put("studentName", recipient.getFullName());
        EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(
                context.eventType(), setting.getSubjectTemplate(), setting.getBodyTemplate(), variables);
        String testEventKey = "email-test:" + setting.getEventType() + ":" + UUID.randomUUID();
        EmailOutbox outbox = newOutbox(setting, context, recipient, rendered, testEventKey, LocalDateTime.now(clock));
        return outboxRepository.save(outbox);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimReadyMessages() {
        LocalDateTime now = LocalDateTime.now(clock);
        int batchSize = Math.max(1, Math.min(properties.getMaxPerRun(), 100));
        List<EmailOutbox> messages = outboxRepository.lockReadyForDelivery(now, PageRequest.of(0, batchSize));
        for (EmailOutbox message : messages) {
            message.setStatus(EmailOutboxStatus.SENDING);
            message.setClaimedAt(now);
            message.setAttemptCount(message.getAttemptCount() + 1);
        }
        return messages.stream().map(EmailOutbox::getId).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueManual(EmailNotificationSetting setting, User recipient, EmailEventContext context) {
        Map<String, String> variables = new LinkedHashMap<>(context.variables());
        variables.put("studentName", recipient.getFullName());
        EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(
                context.eventType(), setting.getSubjectTemplate(), setting.getBodyTemplate(), variables);
        LocalDateTime now = LocalDateTime.now(clock);
        insertIfAbsent(newOutbox(setting, context, recipient, rendered, context.eventKey(), now), now);
    }

    public void recoverStaleClaims() {
        deliveryStateService.recoverStaleClaims();
    }

    /** SMTP communication happens after the original business transaction has committed. */
    public void deliver(Long outboxId) {
        Optional<DeliveryPayload> payload = loadDeliveryPayload(outboxId);
        if (payload.isEmpty()) {
            return;
        }
        DeliveryPayload message = payload.get();
        if (!message.recipientActive()) {
            deliveryStateService.markFailure(outboxId, "Recipient account is no longer active.", false);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            deliveryStateService.markFailure(outboxId, "SMTP sender is not configured in this environment.", false);
            return;
        }
        try {
            var mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setTo(message.recipientEmail());
            helper.setFrom(new InternetAddress(message.fromEmail(), message.senderName(), StandardCharsets.UTF_8.name()));
            if (message.replyToEmail() != null) {
                helper.setReplyTo(message.replyToEmail());
            }
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            sender.send(mimeMessage);
            deliveryStateService.markSent(outboxId);
        } catch (Exception exception) {
            deliveryStateService.markFailure(
                    outboxId, exception.getClass().getSimpleName() + ": SMTP delivery failed. Check mail server configuration and connectivity.", true);
        }
    }

    protected Optional<DeliveryPayload> loadDeliveryPayload(Long outboxId) {
        return outboxRepository.findByIdAndStatus(outboxId, EmailOutboxStatus.SENDING)
                .map(message -> new DeliveryPayload(
                        message.getRecipientUser().getStatus() == UserStatus.ACTIVE,
                        message.getRecipientEmail(),
                        message.getSenderName(),
                        message.getFromEmail(),
                        message.getReplyToEmail(),
                        message.getSubject(),
                        message.getBody()));
    }

    private EmailOutbox newOutbox(
            EmailNotificationSetting setting,
            EmailEventContext context,
            User recipient,
            EmailTemplateRenderer.RenderedEmail rendered,
            String eventKey,
            LocalDateTime now) {
        EmailOutbox outbox = new EmailOutbox();
        outbox.setEventType(context.eventType());
        outbox.setEventKey(eventKey);
        outbox.setRecipientUser(recipient);
        outbox.setRecipientEmail(recipient.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        outbox.setRecipientName(recipient.getFullName());
        outbox.setSenderName(setting.getSenderName());
        outbox.setFromEmail(setting.getFromEmail());
        outbox.setReplyToEmail(setting.getReplyToEmail());
        outbox.setSubject(rendered.subject());
        outbox.setBody(rendered.body());
        outbox.setStatus(EmailOutboxStatus.PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setFacultyId(context.facultyId());
        outbox.setRelatedPeriodId(context.periodId());
        outbox.setRelatedGroupId(context.groupId());
        outbox.setRelatedTopicId(context.topicId());
        outbox.setRelatedAssignmentId(context.assignmentId());
        outbox.setRelatedMilestoneId(context.milestoneId());
        outbox.setResolvedScopeType(setting.getScopeType());
        outbox.setResolvedScopeId(setting.getScopeId());
        outbox.setCreatedBy(context.actorUserId() == null
                ? null
                : userRepository.getReferenceById(context.actorUserId()));
        return outbox;
    }

    private void insertIfAbsent(EmailOutbox outbox, LocalDateTime createdAt) {
        outboxRepository.insertIfAbsent(
                outbox.getEventType().name(),
                outbox.getEventKey(),
                outbox.getRecipientUser().getId(),
                outbox.getRecipientEmail(),
                outbox.getRecipientName(),
                outbox.getSenderName(),
                outbox.getFromEmail(),
                outbox.getReplyToEmail(),
                outbox.getSubject(),
                outbox.getBody(),
                outbox.getStatus().name(),
                outbox.getAttemptCount(),
                outbox.getNextAttemptAt(),
                outbox.getFacultyId(),
                outbox.getRelatedPeriodId(),
                outbox.getRelatedGroupId(),
                outbox.getRelatedTopicId(),
                outbox.getRelatedAssignmentId(),
                outbox.getRelatedMilestoneId(),
                outbox.getResolvedScopeType().name(),
                outbox.getResolvedScopeId(),
                outbox.getCreatedBy() == null ? null : outbox.getCreatedBy().getId(),
                createdAt);
    }

    private record DeliveryPayload(
            boolean recipientActive,
            String recipientEmail,
            String senderName,
            String fromEmail,
            String replyToEmail,
            String subject,
            String body) {
    }
}
