package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.EmailQueuedResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.EmailRecipientResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.SendEmailRequest;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmailDispatchController {
    private final EmailDispatchService service;

    @GetMapping("/email-recipients")
    public ApiResponse<PageResponse<EmailRecipientResponse>> recipients(
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.recipients(principal.id(), role, search, page, size));
    }

    @PostMapping("/notification-settings/{eventType}/send")
    public ApiResponse<EmailQueuedResponse> send(
            @PathVariable EmailNotificationEventType eventType,
            @Valid @RequestBody SendEmailRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã xếp hàng gửi email đến người nhận đã chọn.",
                service.send(principal.id(), eventType, request));
    }
}
