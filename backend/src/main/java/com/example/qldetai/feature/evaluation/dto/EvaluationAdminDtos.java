package com.example.qldetai.feature.evaluation.dto;

import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.CommitteeStatus;
import com.example.qldetai.feature.evaluation.domain.DefenseStatus;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.domain.ResultStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EvaluationAdminDtos {

    private EvaluationAdminDtos() {
    }

    public record CreateCommitteeRequest(@NotNull Long periodId, @NotBlank String name) {
    }

    public record AddCommitteeMemberRequest(
            @NotNull Long lecturerId,
            @Min(1) @Max(5) int slotNo,
            @NotNull CommitteeRole role) {
    }

    public record CreateDefenseRequest(
            @NotNull Long assignmentId,
            @NotNull Long committeeId,
            @NotBlank String location,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt) {
    }

    public record ReviewGradeChangeRequest(@NotNull Boolean approved, @Size(max = 5000) String comment) {
    }

    public record CreateGradeChangeRequest(@NotBlank @Size(max = 5000) String reason) {
    }

    public record CommitteeMemberResponse(
            Long id,
            Long lecturerId,
            String lecturerCode,
            String lecturerName,
            int slotNo,
            CommitteeRole role) {
    }

    public record EvaluationCommitteeResponse(
            Long id,
            Long periodId,
            String periodName,
            Long facultyId,
            String facultyName,
            String name,
            CommitteeStatus status,
            Long createdBy,
            String createdByName,
            LocalDateTime createdAt,
            int version,
            List<CommitteeMemberResponse> members) {
    }

    public record DefenseSessionResponse(
            Long id,
            Long assignmentId,
            Long groupId,
            String groupName,
            Long topicId,
            String topicCode,
            String topicTitle,
            Long committeeId,
            String committeeName,
            Long periodId,
            String periodName,
            String location,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            DefenseStatus status,
            int version) {
    }

    public record ResultResponse(
            Long id,
            Long assignmentId,
            int versionNo,
            BigDecimal finalScore,
            ResultStatus status) {
    }

    public record ResultInputResponse(
            Long id,
            Long sheetId,
            Long componentId,
            String componentCode,
            int sheetVersion,
            BigDecimal scoreSnapshot,
            String rubricSnapshot) {
    }

    public record ResultMemberResponse(
            Long studentId,
            String studentCodeSnapshot,
            String fullNameSnapshot) {
    }

    public record ResultDetailResponse(
            Long id,
            Long assignmentId,
            Long groupId,
            String groupName,
            Long topicId,
            String topicCode,
            String topicTitle,
            Long periodId,
            String periodName,
            int versionNo,
            BigDecimal finalScore,
            ResultStatus status,
            Long finalizedBy,
            String finalizedByName,
            LocalDateTime finalizedAt,
            Long publishedBy,
            String publishedByName,
            LocalDateTime publishedAt,
            List<ResultInputResponse> inputs,
            List<ResultMemberResponse> members) {
    }

    public record GradeChangeRequestResponse(
            Long id,
            Long sheetId,
            Long evaluationId,
            Long assignmentId,
            Long requestedBy,
            String requestedByName,
            String reason,
            GradeChangeStatus status,
            Long reviewedBy,
            String reviewedByName,
            String reviewComment,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt,
            LocalDateTime appliedAt) {
    }
}
