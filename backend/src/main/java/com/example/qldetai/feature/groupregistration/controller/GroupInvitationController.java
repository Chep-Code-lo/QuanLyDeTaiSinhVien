package com.example.qldetai.feature.groupregistration.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateGroupInvitationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.GroupInvitationResponse;
import com.example.qldetai.feature.groupregistration.service.GroupInvitationService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/group-invitations")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class GroupInvitationController {

    private final GroupInvitationService groupInvitationService;

    @GetMapping("/mine")
    public ApiResponse<PageResponse<GroupInvitationResponse>> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false)
                    com.example.qldetai.feature.groupregistration.domain.GroupInvitationStatus
                            status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(groupInvitationService.mine(principal.id(), status, page, size));
    }

    @GetMapping("/groups/{groupId}")
    public ApiResponse<List<GroupInvitationResponse>> listForGroup(
            @PathVariable Long groupId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(groupInvitationService.listForGroup(groupId, principal.id()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupInvitationResponse>> create(
            @Valid @RequestBody CreateGroupInvitationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        GroupInvitationResponse created = groupInvitationService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/group-invitations/" + created.id()))
                .body(ApiResponse.success("Đã gửi lời mời tham gia nhóm.", created));
    }

    @PatchMapping("/{id}/accept")
    public ApiResponse<GroupInvitationResponse> accept(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã xử lý lời mời tham gia nhóm.",
                groupInvitationService.accept(id, principal.id()));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<GroupInvitationResponse> reject(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã xử lý lời mời tham gia nhóm.",
                groupInvitationService.reject(id, principal.id()));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<GroupInvitationResponse> cancel(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã hủy lời mời tham gia nhóm.", groupInvitationService.cancel(id, principal.id()));
    }
}
