package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.*;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EvaluationController {
 private final EvaluationService service;
 public EvaluationController(EvaluationService service) { this.service = service; }
 @PostMapping("/rubrics") @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
 public ResponseEntity<ApiResponse<RubricResponse>> rubric(@Valid @RequestBody CreateRubricRequest request, @AuthenticationPrincipal UserPrincipal principal) { RubricResponse result=service.createRubric(request,principal.id()); return ResponseEntity.created(URI.create("/api/v1/rubrics/"+result.id())).body(ApiResponse.success("Rubric created.",result)); }
 @PostMapping("/evaluation-assignments") @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
 public ResponseEntity<ApiResponse<Long>> assign(@Valid @RequestBody CreateEvaluationRequest request, @AuthenticationPrincipal UserPrincipal principal) { Long id=service.assign(request,principal.id()); return ResponseEntity.created(URI.create("/api/v1/evaluation-assignments/"+id)).body(ApiResponse.success("Evaluator assigned.",id)); }
 @GetMapping("/evaluation-assignments/{id}/score-sheet") @PreAuthorize("hasRole('LECTURER')")
 public ApiResponse<ScoreSheetResponse> mine(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal principal) { return ApiResponse.success(service.mySheet(id,principal.id())); }
 @PutMapping("/evaluation-assignments/{id}/score-sheet") @PreAuthorize("hasRole('LECTURER')")
 public ApiResponse<ScoreSheetResponse> save(@PathVariable Long id,@Valid @RequestBody SaveSheetRequest request,@AuthenticationPrincipal UserPrincipal principal) { return ApiResponse.success("Score sheet saved.",service.saveSheet(id,request,principal.id())); }
}
