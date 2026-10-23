package com.example.qldetai.feature.identity.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CreateUserRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserAccessRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserRolesRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UpdateUserStatusRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UserResponse;
import com.example.qldetai.feature.identity.service.UserAdminService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(userAdminService.list(search, page, size));
    }

    @GetMapping("/lookup")
    public ApiResponse<UserResponse> lookup(
            @RequestParam @NotBlank @Size(max = 255) String identifier) {
        return ApiResponse.success(userAdminService.lookup(identifier));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.success(userAdminService.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id()))
                .body(ApiResponse.success("Đã tạo tài khoản người dùng.", created));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(
                "Đã cập nhật tài khoản người dùng.", userAdminService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(
                "Đã cập nhật trạng thái người dùng.", userAdminService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        userAdminService.delete(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/access")
    public ApiResponse<UserResponse> updateAccess(
            @PathVariable Long id, @Valid @RequestBody UpdateUserAccessRequest request) {
        return ApiResponse.success(
                "Đã cập nhật quyền truy cập.", userAdminService.updateAccess(id, request));
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserResponse> updateRoles(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRolesRequest request) {
        return ApiResponse.success(
                "Đã cập nhật vai trò người dùng.", userAdminService.updateRoles(id, request));
    }
}
