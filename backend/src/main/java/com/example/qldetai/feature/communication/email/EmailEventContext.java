package com.example.qldetai.feature.communication.email;

import java.util.List;
import java.util.Map;

/** Immutable event snapshot used to choose a setting and create outbox rows. */
public record EmailEventContext(
        EmailNotificationEventType eventType,
        String eventKey,
        Long facultyId,
        Long periodId,
        Long groupId,
        Long topicId,
        Long assignmentId,
        Long milestoneId,
        Long actorUserId,
        List<Long> directRecipientUserIds,
        List<Long> responsibleLecturerUserIds,
        Map<String, String> variables) {}
