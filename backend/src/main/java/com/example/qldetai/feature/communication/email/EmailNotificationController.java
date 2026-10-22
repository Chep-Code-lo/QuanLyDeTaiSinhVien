package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailLogDetailResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailLogResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailNotificationSettingResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailPreviewResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailTestQueuedResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.PreviewNotificationSettingRequest;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.RecipientCandidatesResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.SendTestEmailRequest;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.UpdateNotificationSettingRequest;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmailNotificationController {

    private final EmailNotificationSettingsService settingsService;
    private final EmailLogService emailLogService;


    @GetMapping("/notification-settings")
    // The service verifies ADMIN, active AcademicAppointment(DEAN), or lecturer
    // scope against database relations. Do not model DEAN as a synthetic role.
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<EmailNotificationSettingResponse>> listSettings(
            @RequestParam(required = false) NotificationScopeType scopeType,
            @RequestParam(required = false) Long scopeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(settingsService.list(principal.id(), scopeType, scopeId));
    }

    @GetMapping("/notification-settings/recipients")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RecipientCandidatesResponse> recipients(
            @RequestParam EmailNotificationEventType eventType,
            @RequestParam(required = false) NotificationScopeType scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long topicId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(settingsService.recipients(
                principal.id(), eventType, scopeType, scopeId, facultyId, groupId, topicId));
    }

    @GetMapping("/notification-settings/{eventType}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<EmailNotificationSettingResponse> getSetting(
            @PathVariable EmailNotificationEventType eventType,
            @RequestParam(required = false) NotificationScopeType scopeType,
            @RequestParam(required = false) Long scopeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(settingsService.get(principal.id(), eventType, scopeType, scopeId));
    }

    @PutMapping("/notification-settings/{eventType}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<EmailNotificationSettingResponse> updateSetting(
            @PathVariable EmailNotificationEventType eventType,
            @Valid @RequestBody UpdateNotificationSettingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Đã lưu cài đặt thông báo email.", settingsService.update(principal.id(), eventType, request));
    }

    @PostMapping("/notification-settings/{eventType}/preview")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<EmailPreviewResponse> preview(
            @PathVariable EmailNotificationEventType eventType,
            @Valid @RequestBody PreviewNotificationSettingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(settingsService.preview(principal.id(), eventType, request));
    }

    @PostMapping("/notification-settings/{eventType}/test")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<EmailTestQueuedResponse> test(
            @PathVariable EmailNotificationEventType eventType,
            @Valid @RequestBody SendTestEmailRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Đã xếp hàng gửi email thử đến tài khoản hiện tại.",
                settingsService.queueTest(principal.id(), eventType, request));
    }

    @GetMapping("/email-logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<EmailLogResponse>> listLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(emailLogService.list(principal.id(), page, size));
    }

    @GetMapping("/email-logs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<EmailLogDetailResponse> getLog(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(emailLogService.get(principal.id(), id));
    }
}
