package com.example.qldetai.feature.academic.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.ChangePeriodStatusRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.RegistrationPeriodResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.UpdateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.service.RegistrationPeriodService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/registration-periods")
@RequiredArgsConstructor
public class RegistrationPeriodController {

    private final RegistrationPeriodService registrationPeriodService;


    @GetMapping
    public ApiResponse<PageResponse<RegistrationPeriodResponse>> list(
            @RequestParam(required = false) Long facultyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(registrationPeriodService.list(facultyId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RegistrationPeriodResponse> get(@PathVariable Long id) {
        return ApiResponse.success(registrationPeriodService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageFaculty(#request.facultyId, authentication)")
    public ResponseEntity<ApiResponse<RegistrationPeriodResponse>> create(
            @Valid @RequestBody CreateRegistrationPeriodRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        RegistrationPeriodResponse created = registrationPeriodService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/registration-periods/" + created.id()))
                .body(ApiResponse.success("Đã tạo đợt đăng ký nháp.", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManagePeriod(#id, authentication)")
    public ApiResponse<RegistrationPeriodResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateRegistrationPeriodRequest request) {
        return ApiResponse.success("Đã cập nhật đợt đăng ký.", registrationPeriodService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManagePeriod(#id, authentication)")
    public ApiResponse<RegistrationPeriodResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangePeriodStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Đã cập nhật trạng thái đợt đăng ký.", registrationPeriodService.changeStatus(id, request, principal.id()));
    }
}
