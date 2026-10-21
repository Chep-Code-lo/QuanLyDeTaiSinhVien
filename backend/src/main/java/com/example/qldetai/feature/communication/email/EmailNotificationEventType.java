package com.example.qldetai.feature.communication.email;

/** Business events for which the application can create email outbox records. */
public enum EmailNotificationEventType {
    TASK_ASSIGNED,
    TOPIC_APPROVED,
    TOPIC_REJECTED,
    DEADLINE_REMINDER,
    REGISTRATION_OPENED,
    RESULT_PUBLISHED
}
