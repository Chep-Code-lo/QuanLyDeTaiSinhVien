package com.example.qldetai.feature.communication;

import java.time.LocalDateTime;

public final class CommunicationDtos {

    private CommunicationDtos() {
    }

    public record NotificationResponse(
            Long id,
            String title,
            String content,
            String targetPath,
            LocalDateTime createdAt,
            LocalDateTime readAt) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record AuditLogResponse(
            Long id,
            Long actorId,
            String actorName,
            String action,
            String entityType,
            Long entityId,
            AuditOutcome outcome,
            String requestId,
            Long durationMs,
            LocalDateTime createdAt) {
    }
}
