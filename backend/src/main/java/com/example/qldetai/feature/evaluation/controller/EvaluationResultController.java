package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.domain.ResultStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultDetailResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ResultResponse;
import com.example.qldetai.feature.evaluation.service.EvaluationResultService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluationResultController {

    private final EvaluationResultService service;


    @GetMapping("/results")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<ResultDetailResponse>> results(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(required = false) ResultStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                service.listResults(periodId, assignmentId, status, page, size, principal.id()));
    }

    @GetMapping("/results/{id}")
    public ApiResponse<ResultDetailResponse> result(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.getResult(id, principal.id()));
    }

    @GetMapping("/student/results")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<ResultDetailResponse>> studentResults(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.listPublishedForStudent(principal.id()));
    }

    @PostMapping("/project-assignments/{id}/results/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<ResultResponse> finalize(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Đã chốt kết quả.", service.finalizeResult(id, principal.id()));
    }

    @PostMapping("/results/{id}/publish")
    @PreAuthorize(
            "hasRole('ADMIN') or @evaluationAuthorization.canManageResult(#id, authentication)")
    public ApiResponse<ResultResponse> publish(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Đã công bố kết quả.", service.publish(id, principal.id()));
    }
}
