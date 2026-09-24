package com.example.qldetai.feature.identity.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LecturerDirectoryResponse;
import com.example.qldetai.feature.identity.service.LecturerDirectoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/lecturers")
public class LecturerDirectoryController {

    private final LecturerDirectoryService lecturerDirectoryService;

    public LecturerDirectoryController(LecturerDirectoryService lecturerDirectoryService) {
        this.lecturerDirectoryService = lecturerDirectoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<LecturerDirectoryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(lecturerDirectoryService.list(search, facultyId, departmentId, page, size));
    }
}
