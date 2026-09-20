package com.example.qldetai.feature.progress;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.progress.ProgressDtos.StoredFileResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoredFileService {

    private static final Logger log = LoggerFactory.getLogger(StoredFileService.class);
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final ReportSubmissionRepository reportSubmissionRepository;
    private final Clock clock;
    private final Path storageRoot;
    private final long maxFileSizeBytes;

    public StoredFileService(
            StoredFileRepository storedFileRepository,
            UserRepository userRepository,
            ReportSubmissionRepository reportSubmissionRepository,
            Clock clock,
            @Value("${app.files.storage-root:${java.io.tmpdir}/qldetai-files}") String storageRoot,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.reportSubmissionRepository = reportSubmissionRepository;
        this.clock = clock;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSize.toBytes();
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("The upload size limit must be positive.");
        }
    }

    @Transactional
    public StoredFileResponse store(MultipartFile file, Long actorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose a non-empty file to upload.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessRuleException("The file exceeds the configured upload size limit.");
        }

        User uploadedBy = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String mimeType = normalizeMimeType(file.getContentType());
        String storageKey = buildStorageKey(originalName);
        Path targetPath = resolveTarget(storageKey);
        boolean targetCreated = false;

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
                targetCreated = true;
                removeFileIfTransactionRollsBack(targetPath);
                sizeBytes = copyWithinLimit(digestStream, outputStream);
            }

            if (sizeBytes <= 0) {
                Files.deleteIfExists(targetPath);
                throw new BusinessRuleException("Please choose a non-empty file to upload.");
            }

            StoredFile storedFile = new StoredFile();
            storedFile.setStorageKey(storageKey);
            storedFile.setOriginalName(originalName);
            storedFile.setMimeType(mimeType);
            storedFile.setSizeBytes(sizeBytes);
            storedFile.setSha256(HexFormat.of().formatHex(digest.digest()));
            storedFile.setUploadedBy(uploadedBy);
            storedFile.setUploadedAt(LocalDateTime.now(clock));
            storedFile.setStatus(StoredFileStatus.READY);
            return toResponse(storedFileRepository.saveAndFlush(storedFile));
        } catch (IOException exception) {
            if (targetCreated) {
                deleteStoredBytes(targetPath);
            }
            throw new IllegalStateException("The file could not be stored.", exception);
        } catch (RuntimeException exception) {
            if (targetCreated) {
                deleteStoredBytes(targetPath);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public StoredFileResponse get(Long id, Long actorId) {
        return toResponse(readableFile(id, actorId));
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long id, Long actorId) {
        StoredFile storedFile = readableFile(id, actorId);
        Path targetPath = resolveTarget(storedFile.getStorageKey());
        if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourceNotFoundException("The stored file content was not found.");
        }
        return new FileDownload(new FileSystemResource(targetPath),
                storedFile.getOriginalName(), storedFile.getSizeBytes());
    }

    public record FileDownload(Resource resource, String originalName, long sizeBytes) { }

    private StoredFile readableFile(Long id, Long actorId) {
        StoredFile storedFile = storedFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File was not found."));
        if (storedFile.getStatus() != StoredFileStatus.READY) {
            throw new ResourceNotFoundException("File was not found.");
        }

        // A new upload is private. After submission its project participants may read it.
        boolean owner = storedFile.getUploadedBy().getId().equals(actorId);
        boolean admin = userRepository.findWithRolesById(actorId)
                .map(user -> user.getUserRoles().stream()
                        .anyMatch(userRole -> userRole.getRole().getCode() == RoleCode.ADMIN))
                .orElse(false);
        if (!owner && !admin && !reportSubmissionRepository.canReadFile(id, actorId)) {
            throw new AccessDeniedException("You cannot read this file.");
        }
        return storedFile;
    }

    private long copyWithinLimit(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > maxFileSizeBytes) {
                throw new BusinessRuleException("The file exceeds the configured upload size limit.");
            }
            output.write(buffer, 0, bytesRead);
        }
        return totalBytes;
    }

    private void removeFileIfTransactionRollsBack(Path targetPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        // The database transaction cannot undo a disk write, so we undo it explicitly.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteStoredBytes(targetPath);
                }
            }
        });
    }

    private void deleteStoredBytes(Path targetPath) {
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            log.warn("Could not remove failed upload {}", targetPath, exception);
        }
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
        String suffix = originalName.substring(0, Math.min(originalName.length(), 180));
        return datedPrefix + "/" + UUID.randomUUID() + "-" + suffix;
    }

    private String normalizeOriginalName(String originalFilename) {
        String candidate = originalFilename == null
                ? "upload.bin"
                : originalFilename.replace('\\', '/').substring(originalFilename.replace('\\', '/').lastIndexOf('/') + 1).trim();
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            return "upload.bin";
        }
        if (candidate.length() > 255) {
            throw new BusinessRuleException("The file name must not exceed 255 characters.");
        }
        return candidate.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("[. ]+$", "_");
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentType);
            String normalized = parsed.toString().toLowerCase(Locale.ROOT);
            if (parsed.isWildcardType() || parsed.isWildcardSubtype() || normalized.length() > 100) {
                throw new BusinessRuleException("The file content type is invalid.");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("The file content type is invalid.");
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this runtime.", exception);
        }
    }
}
