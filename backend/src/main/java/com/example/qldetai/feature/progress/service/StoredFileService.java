package com.example.qldetai.feature.progress.service;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.domain.StoredFile;
import com.example.qldetai.feature.progress.domain.StoredFileStatus;
import com.example.qldetai.feature.progress.dto.ProgressDtos.StoredFileResponse;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

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
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoredFileService {

    private static final Map<String, String> ALLOWED_TYPES =
            Map.ofEntries(
                    Map.entry("pdf", "application/pdf"),
                    Map.entry("doc", "application/msword"),
                    Map.entry(
                            "docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    Map.entry("ppt", "application/vnd.ms-powerpoint"),
                    Map.entry(
                            "pptx",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                    Map.entry("xls", "application/vnd.ms-excel"),
                    Map.entry(
                            "xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    Map.entry("zip", "application/zip"),
                    Map.entry("png", "image/png"),
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("txt", "text/plain"));

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Path storageRoot;

    @Value("${spring.servlet.multipart.max-file-size:20MB}")
    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    @Transactional
    public StoredFileResponse store(MultipartFile file, Long actorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose a non-empty file to upload.");
        }

        if (file.getSize() > maxFileSize.toBytes())
            throw new BusinessRuleException("Tệp vượt quá dung lượng cho phép.");
        String originalName = normalizeOriginalName(file.getOriginalFilename());
        if (originalName.length() > 255)
            throw new BusinessRuleException("Tên tệp không được vượt quá 255 ký tự.");
        String mimeType = validatedMimeType(file, originalName);
        String storageKey = buildStorageKey(originalName);
        Path targetPath = resolveTarget(storageKey);

        boolean created = false;
        try {
            Files.createDirectories(targetPath.getParent());
            MessageDigest digest = sha256();
            long sizeBytes;
            try (InputStream inputStream = file.getInputStream();
                    DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
                    OutputStream outputStream =
                            Files.newOutputStream(
                                    targetPath,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE)) {
                created = true;
                cleanOnRollback(targetPath);
                sizeBytes = 0;
                byte[] buffer = new byte[8192];
                int count;
                while ((count = digestStream.read(buffer)) != -1) {
                    sizeBytes += count;
                    if (sizeBytes > maxFileSize.toBytes())
                        throw new BusinessRuleException("Tệp vượt quá dung lượng cho phép.");
                    outputStream.write(buffer, 0, count);
                }
            }

            if (sizeBytes <= 0) {
                Files.deleteIfExists(targetPath);
                throw new BusinessRuleException("Please choose a non-empty file to upload.");
            }

            User uploadedBy =
                    userRepository
                            .findById(actorId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "User account was not found."));

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
            if (created) deleteContent(targetPath);
            throw new IllegalStateException("The file could not be stored.", exception);
        } catch (RuntimeException exception) {
            if (created) deleteContent(targetPath);
            throw exception;
        }
    }

    private void cleanOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        // Unknown commit outcomes may have persisted metadata; retain the content
                        // then.
                        if (status == STATUS_ROLLED_BACK) deleteContent(path);
                    }
                });
    }

    private void deleteContent(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.error(
                    "Unable to clean rolled-back upload. type={}", exception.getClass().getName());
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
            throw new BusinessRuleException(
                    "This file type cannot be previewed. Please download it instead.");
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
        String candidate =
                originalFilename == null
                        ? "upload.bin"
                        : originalFilename
                                .replace('\\', '/')
                                .substring(originalFilename.replace('\\', '/').lastIndexOf('/') + 1)
                                .trim();
        if (candidate.isBlank()) {
            return "upload.bin";
        }
        return candidate.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String validatedMimeType(MultipartFile file, String originalName) {
        int dot = originalName.lastIndexOf('.');
        String extension = dot > 0 ? originalName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String expected = ALLOWED_TYPES.get(extension);
        if (expected == null) {
            throw new BusinessRuleException(
                    "Định dạng tệp không được phép. Chỉ nhận PDF, Office, ZIP, PNG, JPG, JPEG và TXT.");
        }
        String supplied = file.getContentType();
        if (supplied != null && !supplied.isBlank()) {
            try {
                MediaType type = MediaType.parseMediaType(supplied);
                String mime =
                        type.getType().toLowerCase(Locale.ROOT)
                                + "/"
                                + type.getSubtype().toLowerCase(Locale.ROOT);
                if (!expected.equals(mime)
                        && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(mime)
                        && !("zip".equals(extension)
                                && "application/x-zip-compressed".equals(mime))) {
                    throw new BusinessRuleException(
                            "Loại nội dung tệp không khớp với định dạng được phép.");
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessRuleException("Loại nội dung tệp không hợp lệ.");
            }
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            boolean valid =
                    switch (extension) {
                        case "pdf" -> startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2d);
                        case "png" ->
                                startsWith(header, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
                        case "jpg", "jpeg" -> startsWith(header, 0xff, 0xd8, 0xff);
                        case "doc", "ppt", "xls" ->
                                startsWith(header, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
                        case "docx", "pptx", "xlsx", "zip" ->
                                startsWith(header, 0x50, 0x4b, 0x03, 0x04)
                                        || startsWith(header, 0x50, 0x4b, 0x05, 0x06);
                        case "txt" -> isText(input, header);
                        default -> false;
                    };
            if (!valid)
                throw new BusinessRuleException("Nội dung tệp không khớp với định dạng đã chọn.");
            return expected;
        } catch (IOException exception) {
            throw new IllegalStateException("The file could not be read.", exception);
        }
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != prefix[index]) return false;
        }
        return true;
    }

    private boolean isText(InputStream remaining, byte[] header) throws IOException {
        for (byte value : header) {
            if (value == 0) return false;
        }
        byte[] buffer = new byte[8192];
        long total = header.length;
        int count;
        while ((count = remaining.read(buffer)) != -1) {
            total += count;
            if (total > maxFileSize.toBytes())
                throw new BusinessRuleException("Tệp vượt quá dung lượng cho phép.");
            for (int index = 0; index < count; index++) {
                if (buffer[index] == 0) return false;
            }
        }
        return true;
    }

    private boolean isPreviewable(String mimeType) {
        try {
            MediaType mediaType = MediaType.parseMediaType(mimeType);
            return MediaType.APPLICATION_PDF.includes(mediaType)
                    || MediaType.IMAGE_PNG.includes(mediaType)
                    || MediaType.IMAGE_JPEG.includes(mediaType)
                    || MediaType.TEXT_PLAIN.includes(mediaType);
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

    public record DownloadedFile(Path path, String originalName, String mimeType, long sizeBytes) {}
}
