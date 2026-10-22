package com.example.qldetai.feature.progress.dto;

import com.example.qldetai.feature.progress.domain.MilestoneStatus;
import com.example.qldetai.feature.progress.domain.MilestoneType;
import com.example.qldetai.feature.progress.domain.StoredFileStatus;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public final class ProgressDtos {
    private ProgressDtos() {}

    public record StoredFileResponse(
            Long id,
            String storageKey,
            String originalName,
            String mimeType,
            long sizeBytes,
            String sha256,
            LocalDateTime uploadedAt,
            StoredFileStatus status) {}

    public record CreateMilestoneRequest(
            @NotNull Long assignmentId,
            @NotBlank @Size(max = 200) String title,
            @NotNull MilestoneType milestoneType,
            @NotBlank String description,
            @NotNull @Future LocalDateTime dueAt) {}

    public record UpdateMilestoneRequest(
            @Min(0) int version,
            @NotBlank @Size(max = 200) String title,
            @NotNull MilestoneType milestoneType,
            @NotBlank String description,
            @NotNull @Future LocalDateTime dueAt) {}

    public record SubmitReportRequest(@NotNull Long fileId, @Size(max = 5000) String note) {}

    public record FeedbackRequest(
            @NotBlank @Size(max = 5000) String content, @NotNull Boolean accepted) {}

    public record FileResponse(
            Long id, String originalName, String mimeType, long sizeBytes, String sha256) {}

    public record FeedbackResponse(
            Long id,
            Long lecturerId,
            String lecturerName,
            String content,
            LocalDateTime createdAt) {}

    public record SubmissionResponse(
            Long id,
            int versionNo,
            FileResponse file,
            Long submittedBy,
            String submittedByName,
            String note,
            LocalDateTime submittedAt,
            List<FeedbackResponse> feedback) {}

    public record MilestoneResponse(
            Long id,
            Long assignmentId,
            String title,
            MilestoneType milestoneType,
            String description,
            LocalDateTime dueAt,
            MilestoneStatus status,
            int version,
            List<SubmissionResponse> submissions) {}

    public record MilestoneReminderResponse(int recipientCount, int milestoneCount) {}
}
