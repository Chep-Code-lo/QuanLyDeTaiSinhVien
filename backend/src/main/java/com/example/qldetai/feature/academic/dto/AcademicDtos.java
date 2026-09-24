package com.example.qldetai.feature.academic.dto;

import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AcademicDtos {

    private AcademicDtos() {
    }

    public record CreateFacultyRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name) {
    }

    public record UpdateCatalogRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull Boolean active) {
    }

    public record FacultyResponse(Long id, String code, String name, boolean active) {
    }

    public record CreateDepartmentRequest(
            @NotNull Long facultyId,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name) {
    }

    public record DepartmentResponse(
            Long id, Long facultyId, String facultyName, String code, String name, boolean active) {
    }

    public record CreateSemesterRequest(
            @NotBlank @Size(max = 20) String academicYear,
            @Min(1) @Max(3) int semesterNo,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {
    }

    public record SemesterResponse(
            Long id, String academicYear, int semesterNo, LocalDate startsOn, LocalDate endsOn) {
    }

    public record CreateRegistrationPeriodRequest(
            @NotNull Long facultyId,
            @NotNull Long semesterId,
            @NotBlank @Size(max = 200) String name,
            @NotNull ProjectType projectType,
            @NotNull LocalDateTime proposalStartAt,
            @NotNull LocalDateTime proposalEndAt,
            @NotNull LocalDateTime registrationStartAt,
            @NotNull LocalDateTime registrationEndAt,
            LocalDateTime reviewDeadlineAt,
            LocalDate defenseDate) {
    }

    public record UpdateRegistrationPeriodRequest(
            @NotNull Integer version,
            @NotNull Long semesterId,
            @NotBlank @Size(max = 200) String name,
            @NotNull ProjectType projectType,
            @NotNull LocalDateTime proposalStartAt,
            @NotNull LocalDateTime proposalEndAt,
            @NotNull LocalDateTime registrationStartAt,
            @NotNull LocalDateTime registrationEndAt,
            LocalDateTime reviewDeadlineAt,
            LocalDate defenseDate) {
    }

    public record ChangePeriodStatusRequest(
            @NotNull Integer version,
            @NotNull RegistrationPeriodStatus status) {
    }

    public record RegistrationPeriodResponse(
            Long id,
            Long facultyId,
            String facultyName,
            Long semesterId,
            String academicYear,
            int semesterNo,
            String name,
            ProjectType projectType,
            LocalDateTime proposalStartAt,
            LocalDateTime proposalEndAt,
            LocalDateTime registrationStartAt,
            LocalDateTime registrationEndAt,
            LocalDateTime reviewDeadlineAt,
            LocalDate defenseDate,
            RegistrationPeriodStatus status,
            Long createdBy,
            LocalDateTime createdAt,
            int version) {
    }
}
