package com.example.qldetai.feature.dashboard.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.dashboard.dto.DashboardDtos.DashboardSummaryResponse;
import com.example.qldetai.feature.dashboard.service.DashboardService;
import com.example.qldetai.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardSummaryResponse> summary(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(dashboardService.summary(principal.id()));
    }
}
