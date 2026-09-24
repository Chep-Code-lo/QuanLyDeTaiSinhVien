package com.example.qldetai.feature.communication.dto;

import com.example.qldetai.feature.communication.domain.AuditOutcome;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    public record CreateAnnouncementRequest(
            @NotNull Long facultyId,
            @NotBlank @Size(max = 250) String title,
            @NotBlank String content,
            Long attachmentFileId) {
    }

    public record UpdateAnnouncementRequest(
            @NotBlank @Size(max = 250) String title,
            @NotBlank String content,
            Long attachmentFileId) {
    }

    public record AnnouncementResponse(
            Long id,
            Long facultyId,
            String facultyName,
            String title,
            String content,
            Long attachmentFileId,
            String attachmentName,
            String attachmentPath,
            Long createdBy,
            String createdByName,
            AnnouncementStatus status,
            LocalDateTime createdAt,
            LocalDateTime publishedAt) {
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
