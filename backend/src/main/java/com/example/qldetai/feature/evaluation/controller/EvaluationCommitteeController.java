package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.SaveCommitteeRequest;
import com.example.qldetai.feature.evaluation.service.CommitteeConfigurationService;
import com.example.qldetai.feature.evaluation.service.EvaluationCommitteeService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluationCommitteeController {

    private final EvaluationCommitteeService service;
    private final CommitteeConfigurationService configurationService;

    @PostMapping("/evaluation-committees/configuration")
    @PreAuthorize(
            "@academicAuthorization.canManagePeriod(#request.periodId, authentication)")
    public ApiResponse<EvaluationCommitteeResponse> createConfiguration(
            @Valid @RequestBody SaveCommitteeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(configurationService.save(null, request, principal.id()));
    }

    @PutMapping("/evaluation-committees/{id}/configuration")
    @PreAuthorize(
            "@evaluationAuthorization.canManageCommittee(#id, authentication)")
    public ApiResponse<EvaluationCommitteeResponse> updateConfiguration(
            @PathVariable Long id,
            @Valid @RequestBody SaveCommitteeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(configurationService.save(id, request, principal.id()));
    }

    @GetMapping("/evaluation-committees")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<EvaluationCommitteeResponse>> committees(
            @RequestParam(required = false) Long periodId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.listCommittees(periodId, page, size, principal.id()));
    }

    @PostMapping("/evaluation-committees")
    @PreAuthorize(
            "@academicAuthorization.canManagePeriod(#request.periodId,"
                    + " authentication)")
    public ResponseEntity<ApiResponse<Long>> committee(
            @Valid @RequestBody CreateCommitteeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long id = service.createCommittee(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/evaluation-committees/" + id))
                .body(ApiResponse.success("Đã tạo hội đồng.", id));
    }

    @PostMapping("/evaluation-committees/{id}/members")
    @PreAuthorize(
            "@evaluationAuthorization.canManageCommittee(#id, authentication)")
    public ApiResponse<Void> member(
            @PathVariable Long id,
            @Valid @RequestBody AddCommitteeMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.addMember(id, request, principal.id());
        return ApiResponse.success("Đã thêm thành viên hội đồng.", null);
    }
}
