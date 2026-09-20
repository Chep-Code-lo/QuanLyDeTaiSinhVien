package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

class StoredFileServiceTests {

    @TempDir
    Path storageRoot;

    private StoredFileRepository files;
    private UserRepository users;
    private ReportSubmissionRepository submissions;
    private StoredFileService service;
    private User owner;

    @BeforeEach
    void setUp() {
        files = mock(StoredFileRepository.class);
        users = mock(UserRepository.class);
        submissions = mock(ReportSubmissionRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneOffset.UTC);
        service = new StoredFileService(files, users, submissions, clock, storageRoot.toString(), DataSize.ofBytes(100));
        owner = new User();
        owner.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(files.saveAndFlush(any(StoredFile.class))).thenAnswer(invocation -> {
            StoredFile saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
    }

    @Test
    void failedDatabaseSaveRemovesUploadedBytes() throws Exception {
        when(files.saveAndFlush(any(StoredFile.class)))
                .thenThrow(new DataIntegrityViolationException("Database unavailable"));

        assertThatThrownBy(() -> service.store(file(), 1L)).isInstanceOf(DataIntegrityViolationException.class);
        assertNoStoredBytes();
    }

    @Test
    void transactionRollbackRemovesUploadedBytes() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service.store(file(), 1L);
            Path storedPath = storageRoot.resolve(response.storageKey());
            assertThat(storedPath).exists();

            for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            assertThat(storedPath).doesNotExist();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ioFailureRemovesPartialUpload() throws Exception {
        MultipartFile brokenFile = mock(MultipartFile.class);
        when(brokenFile.isEmpty()).thenReturn(false);
        when(brokenFile.getOriginalFilename()).thenReturn("broken.txt");
        when(brokenFile.getSize()).thenReturn(2L);
        when(brokenFile.getInputStream()).thenReturn(new InputStream() {
            private int reads;

            @Override
            public int read() throws IOException {
                if (reads++ == 0) {
                    return 1;
                }
                throw new IOException("Upload interrupted");
            }
        });

        assertThatThrownBy(() -> service.store(brokenFile, 1L)).isInstanceOf(IllegalStateException.class);
        assertNoStoredBytes();
        verify(files, never()).saveAndFlush(any());
    }

    @Test
    void actualByteLimitStillAppliesWhenDeclaredSizeIsIncorrect() throws Exception {
        MockMultipartFile oversized = new MockMultipartFile("file", "report.txt", "text/plain", new byte[101]) {
            @Override
            public long getSize() {
                return 1;
            }
        };

        assertThatThrownBy(() -> service.store(oversized, 1L)).isInstanceOf(BusinessRuleException.class);
        assertNoStoredBytes();
        verify(files, never()).saveAndFlush(any());
    }

    @Test
    void missingUserDoesNotLeaveAFileOnDisk() throws Exception {
        assertThatThrownBy(() -> service.store(file(), 999L)).isInstanceOf(ResourceNotFoundException.class);
        assertNoStoredBytes();
    }

    @Test
    void validatesFilenameAndMimeTypeBeforeWriting() throws Exception {
        MockMultipartFile longName = new MockMultipartFile("file", "a".repeat(256), "text/plain", new byte[] {1});
        MockMultipartFile invalidType = new MockMultipartFile("file", "a.txt", "not a mime type", new byte[] {1});

        assertThatThrownBy(() -> service.store(longName, 1L)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.store(invalidType, 1L)).isInstanceOf(BusinessRuleException.class);
        assertNoStoredBytes();
    }

    @Test
    void assignedParticipantCanReadSubmittedFileMetadata() {
        StoredFile report = new StoredFile();
        report.setId(10L);
        report.setUploadedBy(owner);
        report.setOriginalName("report.pdf");
        report.setStatus(StoredFileStatus.READY);
        when(files.findById(10L)).thenReturn(Optional.of(report));
        when(submissions.canReadFile(10L, 2L)).thenReturn(true);

        assertThat(service.get(10L, 2L).originalName()).isEqualTo("report.pdf");
    }

    @Test
    void deletedFileIsNotReadableEvenByOwner() {
        StoredFile report = new StoredFile();
        report.setId(10L);
        report.setUploadedBy(owner);
        report.setStatus(StoredFileStatus.DELETED);
        when(files.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.get(10L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "C:\\fakepath\\report.txt", "text/plain", new byte[] {1, 2, 3});
    }

    private void assertNoStoredBytes() throws IOException {
        try (var paths = Files.walk(storageRoot)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }
}
