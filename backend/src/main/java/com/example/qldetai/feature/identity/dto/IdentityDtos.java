package com.example.qldetai.feature.identity.dto;

import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record CurrentUserResponse(
            Long id,
            String fullName,
            String username,
            Set<RoleCode> roles,
            StudentProfileResponse student,
            LecturerProfileResponse lecturer,
            java.util.List<AppointmentResponse> activeAppointments) {
    }

    public record LoginResponse(CurrentUserResponse user) {
    }

    public record StudentProfileInput(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 20) String studentCode,
            @NotBlank @Size(max = 50) String className,
            @NotBlank @Size(max = 20) String cohort) {
    }

    public record LecturerProfileInput(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 20) String lecturerCode,
            @Size(max = 50) String academicDegree,
            String specialization) {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 20) String phone,
            @NotEmpty Set<RoleCode> roles,
            @Valid StudentProfileInput student,
            @Valid LecturerProfileInput lecturer) {
    }

    public record UpdateUserStatusRequest(@NotNull UserStatus status) {
    }

    public record UpdateUserRolesRequest(@NotEmpty Set<RoleCode> roles) {
    }

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            String phone,
            UserStatus status,
            Set<RoleCode> roles,
            StudentProfileResponse student,
            LecturerProfileResponse lecturer,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            int version) {
    }

    public record StudentProfileResponse(
            Long id, String studentCode, String className, String cohort, Long departmentId) {
    }

    public record LecturerProfileResponse(
            Long id, String lecturerCode, String academicDegree, String specialization, Long departmentId) {
    }

    public record LecturerDirectoryResponse(
            Long id,
            Long userId,
            String lecturerCode,
            String fullName,
            String academicDegree,
            String specialization,
            Long facultyId,
            String facultyName,
            Long departmentId,
            String departmentName) {
    }

    public record CreateAppointmentRequest(
            @NotNull Long lecturerId,
            @NotNull Long facultyId,
            Long departmentId,
            @NotNull PositionCode positionCode,
            @NotNull LocalDateTime startsAt,
            LocalDateTime endsAt) {
    }

    public record EndAppointmentRequest(@NotNull LocalDateTime endsAt) {
    }

    public record AppointmentResponse(
            Long id,
            Long lecturerId,
            String lecturerCode,
            String lecturerName,
            Long facultyId,
            Long departmentId,
            PositionCode positionCode,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
    }
}
