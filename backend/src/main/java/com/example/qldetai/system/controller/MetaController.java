package com.example.qldetai.system.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.system.dto.MetaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only stable API metadata; it contains no business data. */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    @GetMapping("/meta")
    public ApiResponse<MetaResponse> meta() {
        return ApiResponse.success(new MetaResponse("QuanLyDeTaiSinhVien", "v1"));
    }
}
