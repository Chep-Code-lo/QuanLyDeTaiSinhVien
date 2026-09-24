package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.SaveSheetRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.ScoreSheetResponse;
import com.example.qldetai.feature.evaluation.service.EvaluationService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ScoreSheetController {

    private final EvaluationService service;

    public ScoreSheetController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping("/evaluation-assignments/{id}/score-sheet")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<ScoreSheetResponse> mine(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.mySheet(id, principal.id()));
    }

    @PutMapping("/evaluation-assignments/{id}/score-sheet")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<ScoreSheetResponse> save(
            @PathVariable Long id,
            @Valid @RequestBody SaveSheetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Score sheet saved.", service.saveSheet(id, request, principal.id()));
    }
}
