package com.example.qldetai.feature.communication.email;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.evaluation.domain.ResultVersion;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.progress.domain.Milestone;
import com.example.qldetai.feature.progress.domain.MilestoneStatus;
import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicReview;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes transactional-outbox messages after the existing in-app notification is recorded. */
@Service
@RequiredArgsConstructor
public class EmailNotificationEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationEventService.class);

    private final EmailNotificationSettingsService settingsService;
    private final EmailRecipientResolver recipientResolver;
    private final EmailOutboxService outboxService;
    private final EmailEventContextFactory contextFactory;
    private final MilestoneRepository milestoneRepository;
    private final EmailNotificationProperties properties;
    private final Clock clock;


    @Transactional(propagation = Propagation.MANDATORY)
    public void taskAssigned(ProjectAssignment assignment, User actor) {
        enqueue(contextFactory.taskAssigned(assignment, actor));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void topicRegistrationDecision(TopicRegistration registration, User actor) {
        enqueue(contextFactory.topicRegistrationDecision(registration, actor));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void topicDecision(Topic topic, TopicReview review) {
        enqueue(contextFactory.topicDecision(topic, review));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrationOpened(RegistrationPeriod period, User actor) {
        enqueue(contextFactory.registrationOpened(period, actor));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resultPublished(ResultVersion result) {
        enqueue(contextFactory.resultPublished(result));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void manualDeadlineReminder(Milestone milestone, LocalDate reminderDate) {
        enqueue(contextFactory.manualDeadlineReminder(milestone, reminderDate));
    }

    /** Scheduler entry point. The idempotent event key includes the configured reminder threshold. */
    @Transactional
    public void enqueueDueDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Milestone> candidates = milestoneRepository.findByStatusInAndDueAtBetween(
                EnumSet.of(MilestoneStatus.OPEN, MilestoneStatus.REVISION_REQUIRED),
                now,
                now.plusHours(48));
        for (Milestone milestone : candidates) {
            EmailEventContext probe = contextFactory.deadlineReminder(milestone);
            Optional<EmailNotificationSetting> setting = settingsService.resolveForEvent(probe);
            if (setting.isEmpty() || !setting.get().isEnabled() || setting.get().getReminderHours() == null) {
                continue;
            }
            if (!isInReminderWindow(milestone, setting.get().getReminderHours(), now)) {
                continue;
            }
            enqueue(contextFactory.deadlineReminder(milestone, setting.get().getReminderHours()), setting.get());
        }
    }

    private boolean isInReminderWindow(Milestone milestone, int reminderHours, LocalDateTime now) {
        long remainingSeconds = Duration.between(now, milestone.getDueAt()).getSeconds();
        long targetSeconds = Duration.ofHours(reminderHours).toSeconds();
        long scanWindowSeconds = Duration.ofMinutes(Math.max(1, properties.getDeadlineScanWindowMinutes())).toSeconds();
        return remainingSeconds <= targetSeconds && remainingSeconds > targetSeconds - scanWindowSeconds;
    }

    private void enqueue(EmailEventContext context) {
        try {
            settingsService.resolveForEvent(context).ifPresent(setting -> enqueue(context, setting));
        } catch (RuntimeException exception) {
            // Email delivery is an optional side effect and must not undo the source business action.
            LOGGER.warn("Email outbox was not created for event type {} and key {}.",
                    context.eventType(), context.eventKey());
        }
    }

    private void enqueue(EmailEventContext context, EmailNotificationSetting setting) {
        if (!setting.isEnabled()) {
            return;
        }
        List<EmailRecipientResolver.ResolvedRecipient> recipients = recipientResolver.resolve(
                settingsService.recipientGroups(setting), context);
        if (!recipients.isEmpty()) {
            outboxService.enqueue(setting, context, recipients);
        }
    }
}
