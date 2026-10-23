package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.AssessmentComponentResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateAssessmentComponentRequest;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

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
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssessmentComponentController {

    private final EvaluationService service;

    @GetMapping("/assessment-components")
    public ApiResponse<List<AssessmentComponentResponse>> components(
            @RequestParam Long periodId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.listComponents(periodId, principal.id()));
    }

    @PostMapping("/assessment-components")
    @PreAuthorize(
            "@academicAuthorization.canManagePeriod(#request.periodId,"
                    + " authentication)")
    public ResponseEntity<ApiResponse<AssessmentComponentResponse>> createComponent(
            @Valid @RequestBody CreateAssessmentComponentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AssessmentComponentResponse created = service.createComponent(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/assessment-components/" + created.id()))
                .body(ApiResponse.success("Đã tạo thành phần đánh giá.", created));
    }

    @PatchMapping("/assessment-components/{id}/lock")
    @PreAuthorize(
            "@evaluationAuthorization.canManageAssessmentComponent(#id,"
                    + " authentication)")
    public ApiResponse<AssessmentComponentResponse> lockComponent(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã khóa thành phần đánh giá.", service.lockComponent(id, principal.id()));
    }
}
