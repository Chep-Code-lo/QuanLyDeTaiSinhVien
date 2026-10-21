package com.example.qldetai.feature.progress.service;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.domain.StoredFile;
import com.example.qldetai.feature.progress.domain.StoredFileStatus;
import com.example.qldetai.feature.progress.dto.ProgressDtos.StoredFileResponse;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class StoredFileService {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Path storageRoot;

    @Transactional
    public StoredFileResponse store(MultipartFile file, Long actorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose a non-empty file to upload.");
        }

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String mimeType = normalizeMimeType(file.getContentType());
        String storageKey = buildStorageKey(originalName);
        Path targetPath = resolveTarget(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            MessageDigest digest = sha256();
            long sizeBytes;
            try (InputStream inputStream = file.getInputStream();
                    DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
                    OutputStream outputStream = Files.newOutputStream(
                            targetPath,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                sizeBytes = digestStream.transferTo(outputStream);
            }

            if (sizeBytes <= 0) {
                Files.deleteIfExists(targetPath);
                throw new BusinessRuleException("Please choose a non-empty file to upload.");
            }

            User uploadedBy = userRepository.findById(actorId)
                    .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));

            StoredFile storedFile = new StoredFile();
            storedFile.setStorageKey(storageKey);
            storedFile.setOriginalName(originalName);
            storedFile.setMimeType(mimeType);
            storedFile.setSizeBytes(sizeBytes);
            storedFile.setSha256(HexFormat.of().formatHex(digest.digest()));
            storedFile.setUploadedBy(uploadedBy);
            storedFile.setUploadedAt(LocalDateTime.now(clock));
            storedFile.setStatus(StoredFileStatus.READY);
            return toResponse(storedFileRepository.save(storedFile));
        } catch (IOException exception) {
            throw new IllegalStateException("The file could not be stored.", exception);
        }
    }

    @Transactional(readOnly = true)
    public StoredFileResponse get(Long id) {
        return toResponse(requireFile(id));
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(Long id) {
        StoredFile storedFile = requireFile(id);
        if (storedFile.getStatus() != StoredFileStatus.READY) {
            throw new BusinessRuleException("The file is not available for download.");
        }
        Path path = resolveTarget(storedFile.getStorageKey());
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("The uploaded file content was not found.");
        }
        return new DownloadedFile(
                path,
                storedFile.getOriginalName(),
                storedFile.getMimeType(),
                storedFile.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public DownloadedFile preview(Long id) {
        DownloadedFile file = download(id);
        if (!isPreviewable(file.mimeType())) {
            throw new BusinessRuleException("This file type cannot be previewed. Please download it instead.");
        }
        return file;
    }

    private StoredFileResponse toResponse(StoredFile storedFile) {
        return new StoredFileResponse(
                storedFile.getId(),
                storedFile.getStorageKey(),
                storedFile.getOriginalName(),
                storedFile.getMimeType(),
                storedFile.getSizeBytes(),
                storedFile.getSha256(),
                storedFile.getUploadedAt(),
                storedFile.getStatus());
    }

    private StoredFile requireFile(Long id) {
        return storedFileRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File was not found."));
    }

    private Path resolveTarget(String storageKey) {
        Path targetPath = storageRoot.resolve(storageKey).normalize();
        if (!targetPath.startsWith(storageRoot)) {
            throw new IllegalStateException("Resolved file path escapes the storage root.");
        }
        return targetPath;
    }

    private String buildStorageKey(String originalName) {
        LocalDate today = LocalDate.now(clock);
        String datedPrefix = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        return datedPrefix + "/" + UUID.randomUUID() + "-" + originalName;
    }

    private String normalizeOriginalName(String originalFilename) {
        String candidate = originalFilename == null
                ? "upload.bin"
                : originalFilename.replace('\\', '/').substring(originalFilename.replace('\\', '/').lastIndexOf('/') + 1).trim();
        if (candidate.isBlank()) {
            return "upload.bin";
        }
        return candidate.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private boolean isPreviewable(String mimeType) {
        try {
            MediaType mediaType = MediaType.parseMediaType(mimeType);
            return mediaType.isCompatibleWith(MediaType.APPLICATION_PDF)
                    || "image".equalsIgnoreCase(mediaType.getType())
                    || mediaType.isCompatibleWith(MediaType.TEXT_PLAIN);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this runtime.", exception);
        }
    }

    public record DownloadedFile(Path path, String originalName, String mimeType, long sizeBytes) {
    }
}
