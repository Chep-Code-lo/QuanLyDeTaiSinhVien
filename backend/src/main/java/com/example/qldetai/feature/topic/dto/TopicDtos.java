package com.example.qldetai.feature.topic.dto;

import com.example.qldetai.feature.topic.domain.ProposalSource;
import com.example.qldetai.feature.topic.domain.TopicReviewDecision;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.domain.VisibilityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TopicDtos {

    private TopicDtos() {}

    public record CreateTopicRequest(
            @NotNull Long periodId,
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 300) String title,
            @NotBlank String description,
            @NotBlank String objectives,
            @NotBlank String expectedOutcomes,
            String prerequisites,
            @NotNull VisibilityType visibilityType,
            Long reservedGroupId) {}

    public record CreateRegistrationTopicRequest(
            @NotNull Long periodId,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 300) String title,
            @NotBlank String description,
            @NotBlank String objectives,
            @NotBlank String expectedOutcomes,
            String prerequisites,
            @NotNull Long primaryLecturerId,
            Long secondaryLecturerId) {}

    public record UpdateTopicRequest(
            @NotNull Integer version,
            @NotBlank @Size(max = 300) String title,
            @NotBlank String description,
            @NotBlank String objectives,
            @NotBlank String expectedOutcomes,
            String prerequisites,
            @NotNull VisibilityType visibilityType,
            Long reservedGroupId) {}

    public record AssignSupervisorsRequest(
            @NotNull Integer version, @NotNull Long primaryLecturerId, Long secondaryLecturerId) {}

    public record ReviewTopicRequest(
            @NotNull Integer version,
            @NotNull TopicReviewDecision decision,
            @NotBlank String comment) {}

    public record PublishTopicRequest(@NotNull Integer version) {}

    public record WithdrawTopicRequest(@NotNull Integer version, @NotBlank String reason) {}

    public record TopicSupervisorResponse(
            Long lecturerId, String lecturerCode, String lecturerName, int slotNo) {}

    public record TopicReviewResponse(
            Long id,
            TopicReviewDecision decision,
            String comment,
            Long reviewedBy,
            String reviewerName,
            LocalDateTime reviewedAt) {}

    public record TopicResponse(
            Long id,
            Long periodId,
            String periodName,
            Long facultyId,
            String facultyName,
            Long departmentId,
            String departmentName,
            String code,
            String title,
            String description,
            String objectives,
            String expectedOutcomes,
            String prerequisites,
            Long proposedBy,
            String proposerName,
            ProposalSource proposalSource,
            VisibilityType visibilityType,
            Long reservedGroupId,
            String reservedGroupName,
            TopicStatus status,
            boolean availableForRegistration,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            int version,
            List<TopicSupervisorResponse> supervisors,
            TopicReviewResponse latestReview) {}
}
