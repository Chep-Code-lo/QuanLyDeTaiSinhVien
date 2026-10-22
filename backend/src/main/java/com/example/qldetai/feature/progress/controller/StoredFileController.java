package com.example.qldetai.feature.progress.controller;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.progress.dto.ProgressDtos.StoredFileResponse;
import com.example.qldetai.feature.progress.service.StoredFileService;
import com.example.qldetai.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class StoredFileController {

    private final StoredFileService storedFileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredFileResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        StoredFileResponse storedFile = storedFileService.store(file, principal.id());
        return ResponseEntity.created(URI.create("/api/v1/files/" + storedFile.id()))
                .body(ApiResponse.success("Đã tải tệp lên.", storedFile));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@storedFileAuthorization.canView(#id, authentication)")
    public ApiResponse<StoredFileResponse> get(@PathVariable Long id) {
        return ApiResponse.success(storedFileService.get(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("@storedFileAuthorization.canView(#id, authentication)")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return fileResponse(storedFileService.download(id), true);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("@storedFileAuthorization.canView(#id, authentication)")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        return fileResponse(storedFileService.preview(id), false);
    }

    private ResponseEntity<Resource> fileResponse(
            StoredFileService.DownloadedFile file, boolean attachment) {
        ContentDisposition contentDisposition =
                attachment
                        ? ContentDisposition.attachment()
                                .filename(file.originalName(), StandardCharsets.UTF_8)
                                .build()
                        : ContentDisposition.inline()
                                .filename(file.originalName(), StandardCharsets.UTF_8)
                                .build();
        var response =
                ResponseEntity.ok()
                        .contentType(contentType(file.mimeType()))
                        .contentLength(file.sizeBytes())
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                        .header("X-Content-Type-Options", "nosniff");
        if (!attachment) response.header("Content-Security-Policy", "sandbox; default-src 'none'");
        return response.body(new FileSystemResource(file.path()));
    }

    private MediaType contentType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
