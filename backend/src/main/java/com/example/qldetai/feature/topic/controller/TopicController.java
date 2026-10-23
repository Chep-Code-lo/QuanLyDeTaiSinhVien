package com.example.qldetai.feature.topic.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.dto.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateRegistrationTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.PublishTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.ReviewTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.dto.TopicDtos.UpdateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.WithdrawTopicRequest;
import com.example.qldetai.feature.topic.service.TopicService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public ApiResponse<PageResponse<TopicResponse>> list(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) TopicStatus status,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 200)
                    String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                topicService.list(periodId, status, q, page, size, principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TopicResponse> get(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(topicService.get(id, principal.id()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LECTURER','STUDENT')")
    public ResponseEntity<ApiResponse<TopicResponse>> create(
            @Valid @RequestBody CreateTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TopicResponse created = topicService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id()))
                .body(ApiResponse.success("Đã tạo đề xuất đề tài.", created));
    }

    @PostMapping("/for-registration")
    @PreAuthorize(
            "@academicAuthorization.canManageDepartment(#request.departmentId, authentication)")
    public ResponseEntity<ApiResponse<TopicResponse>> createForRegistration(
            @Valid @RequestBody CreateRegistrationTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TopicResponse created = topicService.createForRegistration(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id()))
                .body(ApiResponse.success("Đã tạo và công bố đề tài để đăng ký.", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LECTURER','STUDENT')")
    public ApiResponse<TopicResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã cập nhật đề tài.", topicService.update(id, request, principal.id()));
    }

    @PutMapping("/{id}/supervisors")
    @PreAuthorize("@topicAuthorization.canManageTopic(#id, authentication)")
    public ApiResponse<TopicResponse> assignSupervisors(
            @PathVariable Long id,
            @Valid @RequestBody AssignSupervisorsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã phân công giảng viên hướng dẫn.",
                topicService.assignSupervisors(id, request, principal.id()));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("@topicAuthorization.canManageTopic(#id, authentication)")
    public ApiResponse<TopicResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã ghi nhận kết quả xét duyệt đề tài.",
                topicService.review(id, request, principal.id()));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("@topicAuthorization.canManageTopic(#id, authentication)")
    public ApiResponse<TopicResponse> publish(
            @PathVariable Long id,
            @Valid @RequestBody PublishTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã công bố đề tài.", topicService.publish(id, request, principal.id()));
    }

    @PatchMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('LECTURER','STUDENT')")
    public ApiResponse<TopicResponse> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody WithdrawTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã rút đề tài.", topicService.withdraw(id, request, principal.id()));
    }
}
