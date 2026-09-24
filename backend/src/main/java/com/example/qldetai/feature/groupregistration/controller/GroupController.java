package com.example.qldetai.feature.groupregistration.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.AddGroupMemberRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.StudentGroupResponse;
import com.example.qldetai.feature.groupregistration.service.GroupService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StudentGroupResponse>> list(
            @RequestParam(required = false) Long periodId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(groupService.list(periodId, page, size));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentGroupResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(groupService.mine(principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentGroupResponse> get(@PathVariable Long id) {
        return ApiResponse.success(groupService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentGroupResponse>> create(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentGroupResponse created = groupService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/groups/" + created.id()))
                .body(ApiResponse.success("Student group created.", created));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentGroupResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddGroupMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Group member added.", groupService.addMember(id, request, principal.id()));
    }

    @DeleteMapping("/{id}/members/{studentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentGroupResponse> removeMember(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Group member removed.", groupService.removeMember(id, studentId, principal.id()));
    }
}
