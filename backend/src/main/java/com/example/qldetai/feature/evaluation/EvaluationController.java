package com.example.qldetai.feature.evaluation;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateEvaluationRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.CreateRubricRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.RubricResponse;
import com.example.qldetai.feature.evaluation.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.EvaluationDtos.ScoreSheetResponse;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/rubrics")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ResponseEntity<ApiResponse<RubricResponse>> rubric(
            @Valid @RequestBody CreateRubricRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        RubricResponse created = evaluationService.createRubric(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/rubrics/" + created.id()))
                .body(ApiResponse.success("Rubric created.", created));
    }

    @PostMapping("/evaluation-assignments")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ResponseEntity<ApiResponse<Long>> assign(
            @Valid @RequestBody CreateEvaluationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long id = evaluationService.assign(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/evaluation-assignments/" + id))
                .body(ApiResponse.success("Evaluator assigned.", id));
    }

    @GetMapping("/evaluation-assignments/{id}/score-sheet")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<ScoreSheetResponse> mine(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(evaluationService.mySheet(id, principal.id()));
    }

    @PutMapping("/evaluation-assignments/{id}/score-sheet")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<ScoreSheetResponse> save(
            @PathVariable Long id,
            @Valid @RequestBody SaveSheetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Score sheet saved.", evaluationService.saveSheet(id, request, principal.id()));
    }
}
