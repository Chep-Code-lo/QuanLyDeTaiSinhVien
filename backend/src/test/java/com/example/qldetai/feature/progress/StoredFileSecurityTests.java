package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.domain.StoredFile;
import com.example.qldetai.feature.progress.domain.StoredFileStatus;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import com.example.qldetai.feature.progress.service.StoredFileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

class StoredFileSecurityTests {
    @TempDir Path root;
    private StoredFileRepository files;
    private UserRepository users;
    private StoredFileService service;

    @BeforeEach
    void setup() {
        files = mock(StoredFileRepository.class);
        users = mock(UserRepository.class);
        service = new StoredFileService(files, users, Clock.systemUTC(), root);
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        when(files.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({
        "pdf,255044462d,application/pdf",
        "png,89504e470d0a1a0a,image/png",
        "jpg,ffd8ff,image/jpeg",
        "jpeg,ffd8ff,image/jpeg",
        "zip,504b0304,application/zip",
        "doc,d0cf11e0a1b11ae1,application/msword",
        "ppt,d0cf11e0a1b11ae1,application/vnd.ms-powerpoint",
        "xls,d0cf11e0a1b11ae1,application/vnd.ms-excel",
        "docx,504b0304,application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "pptx,504b0304,application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "xlsx,504b0304,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "txt,546578740a,text/plain"
    })
    void acceptsAllowedExtensionsWithMatchingSignaturesAndCanonicalizesGenericMime(
            String extension, String hex, String mime) {
        var result =
                service.store(
                        new MockMultipartFile(
                                "file",
                                "Report." + extension.toUpperCase(),
                                "application/octet-stream",
                                HexFormat.of().parseHex(hex)),
                        1L);
        assertThat(result.mimeType()).isEqualTo(mime);
        assertThat(root.resolve(result.storageKey()))
                .hasBinaryContent(HexFormat.of().parseHex(hex));
    }

    @ParameterizedTest
    @CsvSource({
        "bad.svg,image/svg+xml",
        "bad.html,text/html",
        "bad.exe,application/octet-stream",
        "bad.pdf,text/html",
        "bad.png,image/png",
        "bad.docx,application/zip",
        "bad.txt,text/plain"
    })
    void rejectsDisallowedOrDisguisedFilesBeforeWritingAnything(String name, String mime)
            throws Exception {
        byte[] invalid = new byte[] {0, 1, 2, 3};
        assertThatThrownBy(
                        () -> service.store(new MockMultipartFile("file", name, mime, invalid), 1L))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(files);
        try (var entries = Files.list(root)) {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    void legacySvgCanBeDownloadedButCannotBePreviewed() throws Exception {
        var file = new StoredFile();
        file.setStorageKey("legacy.svg");
        file.setOriginalName("legacy.svg");
        file.setMimeType("image/svg+xml");
        file.setStatus(StoredFileStatus.READY);
        file.setSizeBytes(6L);
        Files.writeString(root.resolve("legacy.svg"), "<svg/>");
        when(files.findById(1L)).thenReturn(Optional.of(file));
        assertThat(service.download(1L).originalName()).isEqualTo("legacy.svg");
        assertThatThrownBy(() -> service.preview(1L)).isInstanceOf(BusinessRuleException.class);
    }
}
