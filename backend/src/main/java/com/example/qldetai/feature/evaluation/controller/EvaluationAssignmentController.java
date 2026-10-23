package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.EvaluationAssignmentResponse;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluationAssignmentController {

    private final EvaluationService service;
    private final com.example.qldetai.feature.evaluation.service.EvaluationAssignmentBatchService
            batchService;

    @PostMapping("/evaluation-assignments/batch")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<
                    com.example.qldetai.feature.evaluation.dto.EvaluationDtos
                            .BatchEvaluationResponse>
            assignBatch(
                    @Valid @RequestBody
                            com.example.qldetai.feature.evaluation.dto.EvaluationDtos
                                            .BatchEvaluationRequest
                                    request,
                    @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(batchService.assign(request, principal.id()));
    }

    @GetMapping("/evaluation-assignments/review-progress")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<
                    com.example.qldetai.feature.evaluation.dto.EvaluationDtos
                            .ReviewProgressResponse>
            reviewProgress(
                    @RequestParam @Min(1) Long periodId,
                    @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.reviewProgress(periodId, principal.id()));
    }

    @GetMapping("/evaluation-assignments")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<EvaluationAssignmentResponse>> assignments(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(required = false) Long lecturerId,
            @RequestParam(required = false) EvaluationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                service.listAssignments(
                        periodId, assignmentId, lecturerId, status, page, size, principal.id()));
    }

    @PostMapping("/evaluation-assignments")
    @PreAuthorize(
            "@projectAuthorization.canManageAssignment(#request.assignmentId,"
                    + " authentication)")
    public ResponseEntity<ApiResponse<Long>> assign(
            @Valid @RequestBody CreateEvaluationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long id = service.assign(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/evaluation-assignments/" + id))
                .body(ApiResponse.success("Đã phân công người chấm.", id));
    }
}
