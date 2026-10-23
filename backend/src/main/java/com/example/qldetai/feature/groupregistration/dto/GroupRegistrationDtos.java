package com.example.qldetai.feature.groupregistration.dto;

import com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus;
import com.example.qldetai.feature.groupregistration.domain.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.domain.GroupStatus;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class GroupRegistrationDtos {

    private GroupRegistrationDtos() {}

    public record CreateGroupRequest(
            @NotNull Long periodId, @NotBlank @Size(max = 150) String name) {}

    public record AddGroupMemberRequest(@NotBlank @Size(max = 20) String studentCode) {}

    public record CreateGroupInvitationRequest(
            @NotNull Long groupId,
            @NotBlank @Size(max = 20) String studentCode,
            @NotNull LocalDateTime expiresAt) {}

    public record GroupInvitationResponse(
            Long id,
            Long groupId,
            String groupName,
            Long periodId,
            String periodName,
            Long invitedStudentId,
            String invitedStudentCode,
            String invitedStudentName,
            Long invitedByStudentId,
            String invitedByName,
            GroupInvitationStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            LocalDateTime respondedAt) {}

    public record GroupMemberResponse(
            Long studentId,
            Long userId,
            String studentCode,
            String fullName,
            String className,
            String cohort,
            int slotNo,
            GroupMemberRole memberRole,
            LocalDateTime joinedAt) {}

    public record StudentGroupResponse(
            Long id,
            Long periodId,
            String periodName,
            String name,
            GroupStatus status,
            Long createdBy,
            String creatorName,
            LocalDateTime createdAt,
            int version,
            List<GroupMemberResponse> members) {}

    public record CreateTopicRegistrationRequest(
            @NotNull Long groupId, @NotNull Long topicId, String motivation) {}

    public record ReviewTopicRegistrationRequest(
            @NotNull Integer version, @NotNull RegistrationStatus status, String decisionReason) {}

    public record WithdrawTopicRegistrationRequest(
            @NotNull Integer version, @NotBlank String decisionReason) {}

    public record TopicRegistrationResponse(
            Long id,
            Long periodId,
            String periodName,
            Long groupId,
            String groupName,
            Long topicId,
            String topicCode,
            String topicTitle,
            Long registeredBy,
            String registeredByName,
            String motivation,
            RegistrationStatus status,
            LocalDateTime registeredAt,
            Long reviewedBy,
            String reviewedByName,
            LocalDateTime reviewedAt,
            String decisionReason,
            int version, Long departmentId, Long facultyId) {}
}
