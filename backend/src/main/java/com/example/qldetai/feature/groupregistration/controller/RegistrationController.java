package com.example.qldetai.feature.groupregistration.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.CreateTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.ReviewTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.TopicRegistrationResponse;
import com.example.qldetai.feature.groupregistration.dto.GroupRegistrationDtos.WithdrawTopicRegistrationRequest;
import com.example.qldetai.feature.groupregistration.service.RegistrationService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1/topic-registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;


    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    public ApiResponse<PageResponse<TopicRegistrationResponse>> list(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(registrationService.list(periodId, status, page, size));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PageResponse<TopicRegistrationResponse>> mine(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                registrationService.listForStudent(principal.id(), periodId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@registrationAuthorization.canViewRegistration(#id, authentication)")
    public ApiResponse<TopicRegistrationResponse> get(@PathVariable Long id) {
        return ApiResponse.success(registrationService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<TopicRegistrationResponse>> create(
            @Valid @RequestBody CreateTopicRegistrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TopicRegistrationResponse created = registrationService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/topic-registrations/" + created.id()))
                .body(ApiResponse.success("Đã gửi đăng ký đề tài.", created));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize(
            "hasRole('ADMIN') or @registrationAuthorization.canManageRegistration(#id,"
                + " authentication)")
    public ApiResponse<TopicRegistrationResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewTopicRegistrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã xử lý đăng ký đề tài.",
                registrationService.review(id, request, principal.id()));
    }

    @PatchMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<TopicRegistrationResponse> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody WithdrawTopicRegistrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã rút đăng ký đề tài.",
                registrationService.withdraw(id, request, principal.id()));
    }
}
