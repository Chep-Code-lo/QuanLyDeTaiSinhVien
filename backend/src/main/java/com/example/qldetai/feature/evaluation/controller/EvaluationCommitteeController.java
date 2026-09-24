package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.service.EvaluationCommitteeService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1")
public class EvaluationCommitteeController {

    private final EvaluationCommitteeService service;

    public EvaluationCommitteeController(EvaluationCommitteeService service) {
        this.service = service;
    }

    @GetMapping("/evaluation-committees")
    public ApiResponse<PageResponse<EvaluationCommitteeResponse>> committees(
            @RequestParam(required = false) Long periodId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.listCommittees(periodId, page, size));
    }

    @PostMapping("/evaluation-committees")
    @PreAuthorize(
            "hasRole('ADMIN') or @academicAuthorization.canManagePeriod(#request.periodId,"
                    + " authentication)")
    public ResponseEntity<ApiResponse<Long>> committee(
            @Valid @RequestBody CreateCommitteeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long id = service.createCommittee(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/evaluation-committees/" + id))
                .body(ApiResponse.success("Committee created.", id));
    }

    @PostMapping("/evaluation-committees/{id}/members")
    @PreAuthorize(
            "hasRole('ADMIN') or @evaluationAuthorization.canManageCommittee(#id, authentication)")
    public ApiResponse<Void> member(
            @PathVariable Long id,
            @Valid @RequestBody AddCommitteeMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.addMember(id, request, principal.id());
        return ApiResponse.success("Committee member added.", null);
    }
}
