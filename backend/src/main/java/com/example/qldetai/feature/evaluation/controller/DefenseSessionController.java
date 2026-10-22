package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateDefenseRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseSessionResponse;
import com.example.qldetai.feature.evaluation.service.DefenseSessionService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
import java.util.List;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DefenseSessionController {

    private final DefenseSessionService service;

    @GetMapping("/defense-sessions/eligibility")
    @PreAuthorize("hasRole('ADMIN') or (@projectAuthorization.canManageAssignment(#assignmentId, authentication) and @evaluationAuthorization.canManageCommittee(#committeeId, authentication))")
    public ApiResponse<com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.DefenseEligibilityResponse> eligibility(
            @RequestParam Long assignmentId, @RequestParam Long committeeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.eligibility(assignmentId, committeeId, principal.id()));
    }


    @GetMapping("/defense-sessions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LECTURER')")
    public ApiResponse<PageResponse<DefenseSessionResponse>> defenses(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long committeeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.listDefenses(periodId, committeeId, page, size, principal.id()));
    }

    @GetMapping("/student/defense-sessions")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<DefenseSessionResponse>> myDefenses(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.listDefensesForStudent(principal.id()));
    }

    @PostMapping("/defense-sessions")
    @PreAuthorize(
            "hasRole('ADMIN') or (@projectAuthorization.canManageAssignment(#request.assignmentId,"
                + " authentication) and"
                + " @evaluationAuthorization.canManageCommittee(#request.committeeId,"
                + " authentication))")
    public ResponseEntity<ApiResponse<Long>> defense(
            @Valid @RequestBody CreateDefenseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long id = service.schedule(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/defense-sessions/" + id))
                .body(ApiResponse.success("Defense scheduled.", id));
    }
}
