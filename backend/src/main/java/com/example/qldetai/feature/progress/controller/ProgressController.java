package com.example.qldetai.feature.progress.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.progress.dto.ProgressDtos.*;
import com.example.qldetai.feature.progress.service.ProgressService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProgressController {
    private final ProgressService service;


    @PostMapping("/milestones")
    @PreAuthorize(
            "hasRole('ADMIN') or @projectAuthorization.canManageAssignment(#request.assignmentId,"
                + " authentication)")
    public ResponseEntity<ApiResponse<MilestoneResponse>> create(
            @Valid @RequestBody CreateMilestoneRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        MilestoneResponse r = service.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/milestones/" + r.id()))
                .body(ApiResponse.success("Milestone created.", r));
    }

    @GetMapping("/project-assignments/{assignmentId}/milestones")
    public ApiResponse<List<MilestoneResponse>> list(
            @PathVariable Long assignmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.list(assignmentId, principal.id()));
    }

    @PostMapping("/milestones/{id}/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submit(
            @PathVariable Long id,
            @Valid @RequestBody SubmitReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubmissionResponse r = service.submit(id, request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/report-submissions/" + r.id()))
                .body(ApiResponse.success("Report submitted.", r));
    }

    @PostMapping("/report-submissions/{id}/feedback")
    @PreAuthorize(
            "hasRole('ADMIN') or @progressAuthorization.canManageSubmission(#id, authentication)")
    public ApiResponse<SubmissionResponse> feedback(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Feedback saved.", service.feedback(id, request, principal.id()));
    }
}
