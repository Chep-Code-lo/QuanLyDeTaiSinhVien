package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.GradeChangeRequestResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.service.GradeChangeService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GradeChangeController {

    private final GradeChangeService service;


    @GetMapping("/grade-change-requests")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<GradeChangeRequestResponse>> changes(
            @RequestParam(required = false) GradeChangeStatus status,
            @RequestParam(required = false) Long sheetId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                service.listChanges(status, sheetId, page, size, principal.id()));
    }

    @PostMapping("/score-sheets/{id}/grade-change-requests")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<ApiResponse<Long>> request(
            @PathVariable Long id,
            @Valid @RequestBody CreateGradeChangeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long q = service.requestChange(id, request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/grade-change-requests/" + q))
                .body(ApiResponse.success("Grade change requested.", q));
    }

    @PatchMapping("/grade-change-requests/{id}/review")
    @PreAuthorize(
            "hasRole('ADMIN') or @evaluationAuthorization.canManageGradeChange(#id,"
                    + " authentication)")
    public ApiResponse<Void> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewGradeChangeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.reviewChange(id, request, principal.id());
        return ApiResponse.success("Grade change request reviewed.", null);
    }
}
