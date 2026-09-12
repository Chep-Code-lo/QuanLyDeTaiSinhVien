package com.example.qldetai.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.dto.system.HealthResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight liveness endpoint for local development and deployment checks. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success("Service is running.", new HealthResponse("UP", Instant.now()));
    }
}
