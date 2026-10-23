package com.example.qldetai.feature.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.TooManyRequestsException;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import com.example.qldetai.feature.progress.service.StoredFileService;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:upload-quota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.files.user-quota=12B", "app.files.max-uploads-per-minute=2", "app.files.max-unattached-per-user=2"
})
class StoredFileQuotaIntegrationTests {
    @TempDir static Path root;
    @Autowired StoredFileService service;
    @Autowired StoredFileRepository files;
    @Autowired UserRepository users;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("app.files.storage-root", () -> root.toString());
    }

    private Long owner() {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@quota.local");
        user.setFullName("Upload quota fixture");
        user.setPasswordHash("test-verifier");
        user.setStatus(UserStatus.ACTIVE);
        return users.saveAndFlush(user).getId();
    }

    private MockMultipartFile file(String text) {
        return new MockMultipartFile("file", "report.txt", "text/plain", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void concurrentUploadsCannotExceedTheOwnersQuota() throws Exception {
        Long owner = owner();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> upload = () -> {
                start.await();
                try { service.store(file("Report\n"), owner); return true; }
                catch (BusinessRuleException rejected) { return false; }
            };
            var first = executor.submit(upload);
            var second = executor.submit(upload);
            start.countDown();
            assertThat(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(files.readyBytesByUser(owner)).isEqualTo(7);
        assertThat(files.countUnattachedByUser(owner)).isEqualTo(1);
        assertThat(service.store(file("Report\n"), owner()).sizeBytes()).isEqualTo(7);
    }

    @Test
    void uploadRateIsLimitedPerUserAndAnotherOwnerIsUnaffected() {
        Long owner = owner();
        service.store(file("abc"), owner);
        service.store(file("abc"), owner);
        assertThatThrownBy(() -> service.store(file("abc"), owner)).isInstanceOf(TooManyRequestsException.class);
        assertThat(files.readyBytesByUser(owner)).isEqualTo(6);
        assertThat(service.store(file("abc"), owner()).sizeBytes()).isEqualTo(3);
    }
}
