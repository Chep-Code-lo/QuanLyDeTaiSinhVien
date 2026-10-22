package com.example.qldetai.feature.evaluation.dto;

import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EvaluationDtos {

    private EvaluationDtos() {}

    public record BatchEvaluationRequest(
            @NotEmpty @Size(max = 100)
                    List<@NotNull @jakarta.validation.Valid CreateEvaluationRequest> assignments) {}

    public record BatchEvaluationResponse(int assignedCount, int skippedCount) {}

    public record CreateAssessmentComponentRequest(
            @NotNull Long periodId,
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull AssessmentComponentType componentType) {}

    public record AssessmentComponentResponse(
            Long id,
            Long periodId,
            String periodName,
            String code,
            String name,
            AssessmentComponentType componentType,
            String status) {}

    public record CriterionRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("10.00") BigDecimal maxScore,
            @Min(1) int sortOrder) {}

    public record CreateRubricRequest(
            @NotNull Long componentId,
            @NotBlank String name,
            @NotEmpty List<CriterionRequest> criteria) {}

    public record CreateEvaluationRequest(
            @NotNull Long assignmentId,
            @NotNull Long rubricId,
            @NotNull Long lecturerId,
            Long defenseId,
            @NotNull @Future LocalDateTime deadlineAt) {}

    public record ScoreRequest(
            @NotNull Long criterionId,
            @NotNull @DecimalMin("0.00") BigDecimal score,
            String comment) {}

    public record SaveSheetRequest(
            @NotNull Integer version,
            String overallComment,
            @NotEmpty List<ScoreRequest> scores,
            boolean submit) {}

    public record CriterionResponse(
            Long id, String name, String description, BigDecimal maxScore, int sortOrder) {}

    public record RubricResponse(
            Long id,
            Long componentId,
            String componentCode,
            String componentName,
            String name,
            int versionNo,
            String status,
            List<CriterionResponse> criteria) {}

    public record EvaluationAssignmentResponse(
            Long id,
            Long assignmentId,
            Long periodId,
            String periodName,
            Long componentId,
            String componentCode,
            String componentName,
            Long rubricId,
            String rubricName,
            int rubricVersionNo,
            Long lecturerId,
            String lecturerCode,
            String lecturerName,
            String evaluatorRole,
            Long defenseId,
            Long groupId,
            String groupName,
            Long topicId,
            String topicCode,
            String topicTitle,
            EvaluationStatus status,
            LocalDateTime deadlineAt,
            Long scoreSheetId,
            ScoreSheetStatus scoreSheetStatus) {}

    public record ReviewProgressResponse(
            long assignedCount, long submittedCount, long draftCount, long notStartedCount) {}

    public record ScoreSheetResponse(
            Long id,
            Long evaluationId,
            ScoreSheetStatus status,
            int version,
            BigDecimal totalScore,
            String overallComment,
            List<ScoreRequest> scores) {}
}
