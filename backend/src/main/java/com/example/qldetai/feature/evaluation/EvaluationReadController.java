package com.example.qldetai.feature.evaluation;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.DefenseSessionResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.GradeChangeRequestResponse;
import com.example.qldetai.feature.evaluation.EvaluationAdminDtos.ResultDetailResponse;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class EvaluationReadController {

    private final EvaluationAdministrationService evaluationAdministrationService;

    public EvaluationReadController(EvaluationAdministrationService evaluationAdministrationService) {
        this.evaluationAdministrationService = evaluationAdministrationService;
    }

    @GetMapping("/evaluation-committees")
    public ApiResponse<PageResponse<EvaluationCommitteeResponse>> committees(
            @RequestParam(required = false) Long periodId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(evaluationAdministrationService.listCommittees(periodId, page, size));
    }

    @GetMapping("/defense-sessions")
    public ApiResponse<PageResponse<DefenseSessionResponse>> defenses(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long committeeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(evaluationAdministrationService.listDefenses(periodId, committeeId, page, size));
    }

    @GetMapping("/results")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
    public ApiResponse<PageResponse<ResultDetailResponse>> results(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(required = false) ResultStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                evaluationAdministrationService.listResults(periodId, assignmentId, status, page, size, principal.id()));
    }

    @GetMapping("/results/{id}")
    public ApiResponse<ResultDetailResponse> result(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(evaluationAdministrationService.getResult(id, principal.id()));
    }

    @GetMapping("/grade-change-requests")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<GradeChangeRequestResponse>> changes(
            @RequestParam(required = false) GradeChangeStatus status,
            @RequestParam(required = false) Long sheetId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                evaluationAdministrationService.listChanges(status, sheetId, page, size, principal.id()));
    }
}
