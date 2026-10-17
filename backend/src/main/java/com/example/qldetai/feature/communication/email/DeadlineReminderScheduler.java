package com.example.qldetai.feature.communication.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class DeadlineReminderScheduler {

    private final EmailNotificationEventService emailNotificationEventService;

    public DeadlineReminderScheduler(EmailNotificationEventService emailNotificationEventService) {
        this.emailNotificationEventService = emailNotificationEventService;
    }

    @Scheduled(fixedDelayString = "${app.email.deadline-scan-delay-ms:900000}")
    public void scan() {
        emailNotificationEventService.enqueueDueDeadlineReminders();
    }
}
