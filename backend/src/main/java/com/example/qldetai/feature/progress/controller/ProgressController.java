package com.example.qldetai.feature.progress.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.progress.dto.ProgressDtos.*;
import com.example.qldetai.feature.progress.service.MilestoneReminderService;
import com.example.qldetai.feature.progress.service.ProgressService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProgressController {
    private final ProgressService service;
    private final MilestoneReminderService milestoneReminderService;

    @PostMapping("/milestones")
    @PreAuthorize(
            "hasRole('ADMIN') or @projectAuthorization.canManageAssignment(#request.assignmentId,"
                    + " authentication)")
    public ResponseEntity<ApiResponse<MilestoneResponse>> create(
            @Valid @RequestBody CreateMilestoneRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        MilestoneResponse r = service.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/milestones/" + r.id()))
                .body(ApiResponse.success("Đã tạo mốc tiến độ.", r));
    }

    @PutMapping("/milestones/{id}")
    @PreAuthorize(
            "hasRole('ADMIN') or @progressAuthorization.canManageMilestone(#id, authentication)")
    public ApiResponse<MilestoneResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateMilestoneRequest request) {
        return ApiResponse.success("Đã cập nhật mốc tiến độ.", service.update(id, request));
    }

    @GetMapping("/project-assignments/{assignmentId}/milestones")
    public ApiResponse<List<MilestoneResponse>> list(
            @PathVariable Long assignmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.list(assignmentId, principal.id()));
    }

    @PostMapping("/project-assignments/{assignmentId}/milestones/reminders")
    @PreAuthorize(
            "hasRole('ADMIN') or @projectAuthorization.canManageAssignment(#assignmentId, authentication)")
    public ApiResponse<MilestoneReminderResponse> sendReminder(@PathVariable Long assignmentId) {
        return ApiResponse.success("Đã gửi nhắc hạn.", milestoneReminderService.send(assignmentId));
    }

    @PostMapping("/milestones/{id}/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submit(
            @PathVariable Long id,
            @Valid @RequestBody SubmitReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubmissionResponse r = service.submit(id, request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/report-submissions/" + r.id()))
                .body(ApiResponse.success("Đã nộp báo cáo.", r));
    }

    @PostMapping("/report-submissions/{id}/feedback")
    @PreAuthorize(
            "@progressAuthorization.canSuperviseSubmission(#id, authentication)")
    public ApiResponse<SubmissionResponse> feedback(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã lưu nhận xét.", service.feedback(id, request, principal.id()));
    }
}
