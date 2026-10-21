package com.example.qldetai.feature.identity.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.AppointmentResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CreateAppointmentRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.EndAppointmentRequest;
import com.example.qldetai.feature.identity.service.AcademicAppointmentService;
import com.example.qldetai.security.UserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/academic-appointments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AcademicAppointmentController {

    private final AcademicAppointmentService academicAppointmentService;


    @GetMapping
    public ApiResponse<List<AppointmentResponse>> list(@RequestParam(required = false) Long lecturerId) {
        return ApiResponse.success(academicAppointmentService.list(lecturerId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AppointmentResponse created = academicAppointmentService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/admin/academic-appointments/" + created.id()))
                .body(ApiResponse.success("Academic appointment created.", created));
    }

    @PatchMapping("/{id}/end")
    public ApiResponse<AppointmentResponse> end(
            @PathVariable Long id, @Valid @RequestBody EndAppointmentRequest request) {
        return ApiResponse.success("Academic appointment ended.", academicAppointmentService.end(id, request));
    }
}
