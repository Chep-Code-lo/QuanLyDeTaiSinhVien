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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoredFileService {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Path storageRoot;

    public StoredFileService(
            StoredFileRepository storedFileRepository,
            UserRepository userRepository,
            Clock clock,
            @Value("${app.files.storage-root:${java.io.tmpdir}/qldetai-files}") String storageRoot) {
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

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
        return toResponse(storedFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File was not found.")));
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

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this runtime.", exception);
        }
    }
}
