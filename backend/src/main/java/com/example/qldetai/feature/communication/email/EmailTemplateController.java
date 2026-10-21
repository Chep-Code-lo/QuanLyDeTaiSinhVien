package com.example.qldetai.feature.communication.email;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.EmailQueuedResponse;
import com.example.qldetai.feature.communication.email.EmailDispatchDtos.SendEmailRequest;
import com.example.qldetai.feature.communication.email.EmailTemplateDtos.EmailTemplateResponse;
import com.example.qldetai.feature.communication.email.EmailTemplateDtos.SaveEmailTemplateRequest;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/email-templates")
@PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
@RequiredArgsConstructor
public class EmailTemplateController {
    private final EmailTemplateService templates;
    private final EmailDispatchService dispatch;

    @GetMapping
    public ApiResponse<PageResponse<EmailTemplateResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(templates.list(principal.id(), page, size));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SaveEmailTemplateRequest request) {
        var created = templates.create(principal.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/email-templates/" + created.id()))
                .body(ApiResponse.success("Email template created.", created));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmailTemplateResponse> update(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SaveEmailTemplateRequest request) {
        return ApiResponse.success(templates.update(id, principal.id(), request));
    }

    @PostMapping("/{id}/send")
    public ApiResponse<EmailQueuedResponse> send(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SendEmailRequest request) {
        return ApiResponse.success("Email was queued for the selected recipients.", dispatch.sendTemplate(principal.id(), id, request));
    }
}
