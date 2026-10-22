package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import com.example.qldetai.feature.progress.service.StoredFileService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:upload-transactions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class StoredFileTransactionIntegrationTests {
    @TempDir static Path root;
    @Autowired StoredFileService service;
    @Autowired StoredFileRepository files;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("app.files.storage-root", () -> root.toString());
    }

    private User owner() {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@upload.local");
        user.setFullName("Upload regression");
        user.setPasswordHash("unused-test-verifier");
        user.setStatus(UserStatus.ACTIVE);
        return users.saveAndFlush(user);
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                "Report".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void outerRollbackRemovesBothMetadataAndContent() {
        var owner = owner();
        var result =
                new AtomicReference<
                        com.example.qldetai.feature.progress.dto.ProgressDtos.StoredFileResponse>();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            result.set(service.store(upload(), owner.getId()));
                            assertThat(root.resolve(result.get().storageKey())).exists();
                            status.setRollbackOnly();
                        });
        assertThat(files.findById(result.get().id())).isEmpty();
        assertThat(root.resolve(result.get().storageKey())).doesNotExist();
    }

    @Test
    void commitRetainsDownloadableContent() {
        var result = service.store(upload(), owner().getId());
        assertThat(files.findById(result.id())).isPresent();
        assertThat(service.download(result.id()).path()).hasContent("Report");
    }

    @Test
    void databaseConstraintFailureAlsoCleansContentAfterRollback() {
        var owner = owner();
        var path = new AtomicReference<Path>();
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactions)
                                        .executeWithoutResult(
                                                status -> {
                                                    var result =
                                                            service.store(upload(), owner.getId());
                                                    path.set(root.resolve(result.storageKey()));
                                                    var metadata =
                                                            files.findById(result.id())
                                                                    .orElseThrow();
                                                    metadata.setOriginalName(null);
                                                    files.saveAndFlush(metadata);
                                                }))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(path.get()).doesNotExist();
    }
}
