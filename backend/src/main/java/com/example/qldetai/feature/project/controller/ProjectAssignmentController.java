package com.example.qldetai.feature.project.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.project.domain.AssignmentStatus;
import com.example.qldetai.feature.project.dto.ProjectDtos.CreateAssignmentRequest;
import com.example.qldetai.feature.project.dto.ProjectDtos.ProjectAssignmentResponse;
import com.example.qldetai.feature.project.dto.ProjectDtos.UpdateAssignmentStatusRequest;
import com.example.qldetai.feature.project.service.ProjectAssignmentService;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
@RequestMapping("/api/v1/project-assignments")
public class ProjectAssignmentController {

    private final ProjectAssignmentService projectAssignmentService;

    public ProjectAssignmentController(ProjectAssignmentService projectAssignmentService) {
        this.projectAssignmentService = projectAssignmentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectAssignmentResponse>> list(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(projectAssignmentService.list(periodId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectAssignmentResponse> get(@PathVariable Long id) {
        return ApiResponse.success(projectAssignmentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageRegistration(#request.registrationId, authentication)")
    public ResponseEntity<ApiResponse<ProjectAssignmentResponse>> create(
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProjectAssignmentResponse created = projectAssignmentService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/project-assignments/" + created.id()))
                .body(ApiResponse.success("Project assignment created.", created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageAssignment(#id, authentication)")
    public ApiResponse<ProjectAssignmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssignmentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Project assignment status updated.",
                projectAssignmentService.updateStatus(id, request, principal.id()));
    }
}
