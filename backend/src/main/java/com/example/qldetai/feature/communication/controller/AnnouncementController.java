package com.example.qldetai.feature.communication.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.AnnouncementResponse;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.CreateAnnouncementRequest;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.UpdateAnnouncementRequest;
import com.example.qldetai.feature.communication.service.AnnouncementService;
import com.example.qldetai.security.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

/** Public feed plus faculty-scoped management APIs for official announcements. */
@Validated
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ApiResponse<PageResponse<AnnouncementResponse>> listPublished(
            @RequestParam(required = false) Long facultyId,
            @RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 250)
                    String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                announcementService.listPublished(facultyId, search, page, size));
    }

    @GetMapping("/manage")
    @PreAuthorize(
            "hasRole('ADMIN') or @academicAuthorization.canManageFaculty(#facultyId, authentication)")
    public ApiResponse<PageResponse<AnnouncementResponse>> listForManagement(
            @RequestParam Long facultyId,
            @RequestParam(required = false) AnnouncementStatus status,
            @RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 250)
                    String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                announcementService.listForManagement(facultyId, status, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @announcementAuthorization.canView(#id, authentication)")
    public ApiResponse<AnnouncementResponse> get(@PathVariable Long id) {
        return ApiResponse.success(announcementService.get(id));
    }

    @PostMapping
    @PreAuthorize(
            "hasRole('ADMIN') or @academicAuthorization.canManageFaculty(#request.facultyId, authentication)")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AnnouncementResponse created = announcementService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/announcements/" + created.id()))
                .body(ApiResponse.success("Đã tạo thông báo nháp.", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @announcementAuthorization.canManage(#id, authentication)")
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return ApiResponse.success(
                "Đã cập nhật thông báo.", announcementService.update(id, request));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN') or @announcementAuthorization.canManage(#id, authentication)")
    public ApiResponse<AnnouncementResponse> publish(@PathVariable Long id) {
        return ApiResponse.success("Đã công bố thông báo.", announcementService.publish(id));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN') or @announcementAuthorization.canManage(#id, authentication)")
    public ApiResponse<AnnouncementResponse> archive(@PathVariable Long id) {
        return ApiResponse.success("Đã lưu trữ thông báo.", announcementService.archive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @announcementAuthorization.canManage(#id, authentication)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
