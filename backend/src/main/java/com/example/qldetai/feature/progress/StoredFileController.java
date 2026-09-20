package com.example.qldetai.feature.progress;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.progress.ProgressDtos.StoredFileResponse;
import com.example.qldetai.security.UserPrincipal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
    public ApiResponse<StoredFileResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(storedFileService.get(id, principal.id()));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        StoredFileService.FileDownload download = storedFileService.download(id, principal.id());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
