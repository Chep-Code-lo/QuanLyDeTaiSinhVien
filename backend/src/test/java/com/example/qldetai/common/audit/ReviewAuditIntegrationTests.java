package com.example.qldetai.common.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:review-audit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "logging.level.org.springframework.web=INFO"
        })
@Import(ReviewAuditIntegrationTests.ProbeConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ReviewAuditIntegrationTests {
    @Autowired FacultyRepository faculties;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ProbeService probe;
    @MockitoSpyBean AuditLogWriter writer;
    private Long id;

    @BeforeEach
    void fixture() {
        var faculty = new Faculty();
        faculty.setCode(UUID.randomUUID().toString().substring(0, 8));
        faculty.setName("Original");
        faculty.setActive(true);
        id = faculties.saveAndFlush(faculty).getId();
    }

    private void outcome(String expected) {
        assertThat(
                        jdbc.queryForList(
                                "select outcome from audit_logs where entity_id = ? and action = 'REVIEW_AUDIT_PROBE'",
                                String.class,
                                id))
                .containsExactly(expected);
    }

    @Test
    void successfulInvocationWritesExactlyOneCommittedSuccess() {
        probe.success(id);
        outcome("SUCCESS");
        assertThat(faculties.findById(id).orElseThrow().getName()).isEqualTo("Changed");
    }

    @Test
    void thrownBusinessFailureRollsBackAndIsNotAuditedAsSuccess() {
        assertThatThrownBy(() -> probe.failure(id)).isInstanceOf(IllegalStateException.class);
        outcome("FAILURE");
        assertThat(faculties.findById(id).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void outerRollbackAfterNormalReturnProducesOnlyOneFailure() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            probe.success(id);
                            status.setRollbackOnly();
                        });
        outcome("FAILURE");
        assertThat(faculties.findById(id).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void auditPersistenceFailureDoesNotUndoBusinessAndDoesNotLogExceptionPayload(
            CapturedOutput output) {
        doThrow(new IllegalStateException("sensitive-audit-payload-probe"))
                .when(writer)
                .write(
                        nullable(Long.class),
                        eq("REVIEW_AUDIT_PROBE"),
                        anyString(),
                        nullable(Long.class),
                        any(),
                        anyString(),
                        anyLong());
        probe.success(id);
        assertThat(faculties.findById(id).orElseThrow().getName()).isEqualTo("Changed");
        assertThat(output.getAll())
                .contains("Unable to persist business audit log")
                .doesNotContain("sensitive-audit-payload-probe");
    }

    @Test
    void unexpectedFailureLoggingDoesNotPrintDiagnosticPayload(CapturedOutput output) {
        var response =
                new com.example.qldetai.exception.GlobalExceptionHandler()
                        .handleUnexpected(
                                new IllegalStateException("sensitive-request-payload-probe"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(output.getAll())
                .contains("Unhandled request failure")
                .doesNotContain("sensitive-request-payload-probe");
    }

    @Test
    void lifecycleCallbacksInitializeAndUpdateUtcTimestampsWithoutReplacingCreationTime() {
        var tx = new TransactionTemplate(transactions);
        var before = LocalDateTime.now(Clock.systemUTC()).minusSeconds(1);
        User saved =
                tx.execute(
                        status -> {
                            var user = new User();
                            user.setEmail(UUID.randomUUID() + "@lifecycle.local");
                            user.setFullName("Timestamp probe");
                            user.setPasswordHash("unused-test-verifier");
                            user.setStatus(UserStatus.ACTIVE);
                            return users.saveAndFlush(user);
                        });
        assertThat(saved.getCreatedAt())
                .isBetween(before, LocalDateTime.now(Clock.systemUTC()).plusSeconds(1));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        // Compare persisted timestamps: the database stores microsecond precision.
        var persistedCreatedAt = users.findById(saved.getId()).orElseThrow().getCreatedAt();
        User updated =
                tx.execute(
                        status -> {
                            var user = users.findById(saved.getId()).orElseThrow();
                            user.setFullName("Changed timestamp probe");
                            return users.saveAndFlush(user);
                        });
        assertThat(updated.getCreatedAt()).isEqualTo(persistedCreatedAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(saved.getUpdatedAt());
        assertThat(updated.getUpdatedAt())
                .isBefore(LocalDateTime.now(Clock.systemUTC()).plusSeconds(1));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ProbeService probeService(FacultyRepository faculties) {
            return new ProbeService(faculties);
        }
    }

    static class ProbeService {
        private final FacultyRepository faculties;

        ProbeService(FacultyRepository faculties) {
            this.faculties = faculties;
        }

        @Transactional
        @BusinessAudit(action = "REVIEW_AUDIT_PROBE", entityType = "FACULTY", entityIdArgument = 0)
        public void success(Long id) {
            faculties.findById(id).orElseThrow().setName("Changed");
        }

        @Transactional
        @BusinessAudit(action = "REVIEW_AUDIT_PROBE", entityType = "FACULTY", entityIdArgument = 0)
        public void failure(Long id) {
            faculties.findById(id).orElseThrow().setName("Changed");
            faculties.flush();
            throw new IllegalStateException("Business operation failed");
        }
    }
}
