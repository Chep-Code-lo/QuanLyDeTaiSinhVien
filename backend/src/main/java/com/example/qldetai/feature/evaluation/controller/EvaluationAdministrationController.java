package com.example.qldetai.feature.evaluation.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.*;
import com.example.qldetai.feature.evaluation.service.EvaluationAdministrationService;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EvaluationAdministrationController {
 private final EvaluationAdministrationService service;
 public EvaluationAdministrationController(EvaluationAdministrationService service) { this.service=service; }
 @PostMapping("/evaluation-committees") @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManagePeriod(#r.periodId, authentication)") public ResponseEntity<ApiResponse<Long>> committee(@Valid @RequestBody CreateCommitteeRequest r,@AuthenticationPrincipal UserPrincipal p){Long id=service.createCommittee(r,p.id());return ResponseEntity.created(URI.create("/api/v1/evaluation-committees/"+id)).body(ApiResponse.success("Committee created.",id));}
 @PostMapping("/evaluation-committees/{id}/members") @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageCommittee(#id, authentication)") public ApiResponse<Void> member(@PathVariable Long id,@Valid @RequestBody AddCommitteeMemberRequest r,@AuthenticationPrincipal UserPrincipal p){service.addMember(id,r,p.id());return ApiResponse.success("Committee member added.",null);}
 @PostMapping("/defense-sessions") @PreAuthorize("hasRole('ADMIN') or (@academicAuthorization.canManageAssignment(#r.assignmentId, authentication) and @academicAuthorization.canManageCommittee(#r.committeeId, authentication))") public ResponseEntity<ApiResponse<Long>> defense(@Valid @RequestBody CreateDefenseRequest r,@AuthenticationPrincipal UserPrincipal p){Long id=service.schedule(r,p.id());return ResponseEntity.created(URI.create("/api/v1/defense-sessions/"+id)).body(ApiResponse.success("Defense scheduled.",id));}
 @PostMapping("/project-assignments/{id}/results/finalize") @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageAssignment(#id, authentication)") public ApiResponse<ResultResponse> finalize(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){return ApiResponse.success("Result finalized.",service.finalizeResult(id,p.id()));}
 @PostMapping("/results/{id}/publish") @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageResult(#id, authentication)") public ApiResponse<ResultResponse> publish(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){return ApiResponse.success("Result published.",service.publish(id,p.id()));}
 @PostMapping("/score-sheets/{id}/grade-change-requests") @PreAuthorize("hasRole('LECTURER')") public ResponseEntity<ApiResponse<Long>> request(@PathVariable Long id,@Valid @RequestBody CreateGradeChangeRequest r,@AuthenticationPrincipal UserPrincipal p){Long q=service.requestChange(id,r,p.id());return ResponseEntity.created(URI.create("/api/v1/grade-change-requests/"+q)).body(ApiResponse.success("Grade change requested.",q));}
 @PatchMapping("/grade-change-requests/{id}/review") @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageGradeChange(#id, authentication)") public ApiResponse<Void> review(@PathVariable Long id,@Valid @RequestBody ReviewGradeChangeRequest r,@AuthenticationPrincipal UserPrincipal p){service.reviewChange(id,r,p.id());return ApiResponse.success("Grade change request reviewed.",null);}
}
