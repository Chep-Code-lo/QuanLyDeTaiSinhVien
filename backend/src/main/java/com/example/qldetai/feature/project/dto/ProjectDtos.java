package com.example.qldetai.feature.project.dto;

import com.example.qldetai.feature.project.domain.AssignmentStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateAssignmentRequest(@NotNull Long registrationId) {
    }

    public record UpdateAssignmentStatusRequest(
            @NotNull Integer version,
            @NotNull AssignmentStatus status) {
    }

    public record ProjectAssignmentResponse(
            Long id,
            Long registrationId,
            Long groupId,
            String groupName,
            Long topicId,
            String topicCode,
            String topicTitle,
            Long periodId,
            String periodName,
            Long assignedBy,
            String assignedByName,
            LocalDateTime assignedAt,
            AssignmentStatus status,
            int version) {
    }
}
