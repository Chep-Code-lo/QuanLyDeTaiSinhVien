package com.example.qldetai.feature.evaluation;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.AssessmentComponentResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateAssessmentComponentRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.EvaluationAssignmentResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.RubricResponse;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
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

@Validated
@RestController
@RequestMapping("/api/v1")
public class EvaluationCatalogController {

    private final EvaluationService evaluationService;

    public EvaluationCatalogController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/assessment-components")
    public ApiResponse<List<AssessmentComponentResponse>> components(@RequestParam(required = false) Long periodId) {
        return ApiResponse.success(evaluationService.listComponents(periodId));
    }

    @PostMapping("/assessment-components")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ResponseEntity<ApiResponse<AssessmentComponentResponse>> createComponent(
            @Valid @RequestBody CreateAssessmentComponentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AssessmentComponentResponse created = evaluationService.createComponent(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/assessment-components/" + created.id()))
                .body(ApiResponse.success("Assessment component created.", created));
    }

    @PatchMapping("/assessment-components/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<AssessmentComponentResponse> lockComponent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Assessment component locked.", evaluationService.lockComponent(id, principal.id()));
    }

    @GetMapping("/rubrics")
    public ApiResponse<List<RubricResponse>> rubrics(@RequestParam(required = false) Long componentId) {
        return ApiResponse.success(evaluationService.listRubrics(componentId));
    }

    @PatchMapping("/rubrics/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<RubricResponse> lockRubric(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Rubric locked.", evaluationService.lockRubric(id, principal.id()));
    }

    @GetMapping("/evaluation-assignments")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<EvaluationAssignmentResponse>> assignments(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(required = false) Long lecturerId,
            @RequestParam(required = false) EvaluationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(evaluationService.listAssignments(periodId, assignmentId, lecturerId, status, page, size));
    }
}
