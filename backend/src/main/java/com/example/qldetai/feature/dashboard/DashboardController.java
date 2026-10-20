package com.example.qldetai.feature.dashboard;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> summary(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.summary(principal.id()));
    }
}
