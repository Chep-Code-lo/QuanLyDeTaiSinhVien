package com.example.qldetai.feature.progress.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.progress.dto.ProgressDtos.StoredFileResponse;
import com.example.qldetai.feature.progress.service.StoredFileService;
import com.example.qldetai.security.UserPrincipal;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class StoredFileController {

    private final StoredFileService storedFileService;

    public StoredFileController(StoredFileService storedFileService) {
        this.storedFileService = storedFileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredFileResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        StoredFileResponse storedFile = storedFileService.store(file, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/files/" + storedFile.id()))
                .body(ApiResponse.success("File uploaded.", storedFile));
    }

    @GetMapping("/{id}")
    public ApiResponse<StoredFileResponse> get(@PathVariable Long id) {
        return ApiResponse.success(storedFileService.get(id));
    }
}
