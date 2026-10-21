package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.RubricResponse;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;

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
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RubricController {

    private final EvaluationService service;


    @GetMapping("/rubrics")
    public ApiResponse<List<RubricResponse>> rubrics(
            @RequestParam Long componentId) {
        return ApiResponse.success(service.listRubrics(componentId));
    }

    @PostMapping("/rubrics")
    @PreAuthorize(
            "hasRole('ADMIN') or"
                    + " @evaluationAuthorization.canManageAssessmentComponent(#request.componentId,"
                    + " authentication)")
    public ResponseEntity<ApiResponse<RubricResponse>> rubric(
            @Valid @RequestBody CreateRubricRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        RubricResponse result = service.createRubric(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/rubrics/" + result.id()))
                .body(ApiResponse.success("Rubric created.", result));
    }

    @PatchMapping("/rubrics/{id}/lock")
    @PreAuthorize(
            "hasRole('ADMIN') or @evaluationAuthorization.canManageRubric(#id, authentication)")
    public ApiResponse<RubricResponse> lockRubric(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Rubric locked.", service.lockRubric(id, principal.id()));
    }
}
