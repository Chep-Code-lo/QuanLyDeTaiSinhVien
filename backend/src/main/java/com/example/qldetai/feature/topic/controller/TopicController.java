package com.example.qldetai.feature.topic.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.dto.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateRegistrationTopicRequest;
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
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TopicResponse>> list(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) TopicStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(topicService.list(periodId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<TopicResponse> get(@PathVariable Long id) {
        return ApiResponse.success(topicService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
    public ResponseEntity<ApiResponse<TopicResponse>> create(
            @Valid @RequestBody CreateTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TopicResponse created = topicService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id()))
                .body(ApiResponse.success("Topic proposal created.", created));
    }

    @PostMapping("/for-registration")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('LECTURER') and @academicAuthorization.canManageDepartment(#request.departmentId, authentication))")
    public ResponseEntity<ApiResponse<TopicResponse>> createForRegistration(
            @Valid @RequestBody CreateRegistrationTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TopicResponse created = topicService.createForRegistration(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id()))
                .body(ApiResponse.success("Đã tạo và công bố đề tài cho sinh viên đăng ký.", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
    public ApiResponse<TopicResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Topic updated.", topicService.update(id, request, principal.id()));
    }

    @PutMapping("/{id}/supervisors")
    @PreAuthorize("hasRole('ADMIN') or @topicAuthorization.canManageTopic(#id, authentication)")
    public ApiResponse<TopicResponse> assignSupervisors(
            @PathVariable Long id,
            @Valid @RequestBody AssignSupervisorsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Topic supervisors assigned.",
                topicService.assignSupervisors(id, request, principal.id()));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN') or @topicAuthorization.canManageTopic(#id, authentication)")
    public ApiResponse<TopicResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Topic review recorded.", topicService.review(id, request, principal.id()));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN') or @topicAuthorization.canManageTopic(#id, authentication)")
    public ApiResponse<TopicResponse> publish(
            @PathVariable Long id,
            @Valid @RequestBody PublishTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Topic published.", topicService.publish(id, request, principal.id()));
    }

    @PatchMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER','STUDENT')")
    public ApiResponse<TopicResponse> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody WithdrawTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Topic withdrawn.", topicService.withdraw(id, request, principal.id()));
    }
}
