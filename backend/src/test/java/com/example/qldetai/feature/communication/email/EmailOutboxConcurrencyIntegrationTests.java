package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.UserRepository;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:outbox-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "logging.level.org.hibernate.SQL=OFF"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailOutboxConcurrencyIntegrationTests {
    @Autowired EmailOutboxService service;
    @Autowired EmailOutboxRepository outbox;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired EmailOutboxDeliveryStateService state;
    @MockitoBean JavaMailSender sender;

    @DynamicPropertySource
    static void isolatedMysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("outbox.test.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:")
                    || !url.contains("/ql_de_tai_sinh_vien?")) {
                throw new IllegalArgumentException(
                        "Outbox tests require an explicitly selected disposable local database.");
            }
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.datasource.username", () -> "migration_test");
            properties.add(
                    "spring.datasource.password",
                    () -> System.getProperty("outbox.test.mysql.password"));
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
            properties.add("spring.sql.init.mode", () -> "never");
        }
    }

    @Test
    void auditReproducesDuplicateSmtpSendAfterStaleClaimRecovery() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Long id = pendingMessage(tx);
        var oldClaim = service.claimReadyMessages().getFirst();
        assertThat(oldClaim.id()).isEqualTo(id);
        var preparing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstPreparation = new AtomicBoolean(true);
        when(sender.createMimeMessage())
                .thenAnswer(
                        invocation -> {
                            if (firstPreparation.compareAndSet(true, false)) {
                                preparing.countDown();
                                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                            }
                            return new MimeMessage(Session.getInstance(new Properties()));
                        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.deliver(oldClaim));
            try {
                assertThat(preparing.await(10, TimeUnit.SECONDS)).isTrue();
                tx.executeWithoutResult(
                        status ->
                                outbox.findById(id)
                                        .orElseThrow()
                                        .setClaimedAt(LocalDateTime.now(clock).minusMinutes(16)));
                service.recoverStaleClaims();
                var newClaim = service.claimReadyMessages().getFirst();
                assertThat(newClaim.id()).isEqualTo(id);
                assertThat(newClaim.token()).isNotEqualTo(oldClaim.token());
                state.markSent(oldClaim);
                state.markFailure(oldClaim, "stale owner", false);
                var claimed = outbox.findById(id).orElseThrow();
                assertThat(claimed.getStatus()).isEqualTo(EmailOutboxStatus.SENDING);
                assertThat(claimed.getClaimToken()).isEqualTo(newClaim.token());
                pool.submit(() -> service.deliver(newClaim)).get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
        }
        verify(sender, times(1)).send(any(MimeMessage.class));
        var stored = outbox.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(stored.getClaimToken()).isNull();
        assertThat(stored.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void rowLockPreventsRecoveryAndDuplicateDeliveryWhileSmtpIsInFlight() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Long id = pendingMessage(tx);
        var claim = service.claimReadyMessages().getFirst();
        tx.executeWithoutResult(
                status ->
                        outbox.findById(id)
                                .orElseThrow()
                                .setClaimedAt(LocalDateTime.now(clock).minusMinutes(16)));
        var sending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var recovering = new CountDownLatch(1);
        when(sender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(
                        invocation -> {
                            sending.countDown();
                            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                            return null;
                        })
                .when(sender)
                .send(any(MimeMessage.class));
        try (var pool = Executors.newFixedThreadPool(3)) {
            var first = pool.submit(() -> service.deliver(claim));
            assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
            var recovery =
                    pool.submit(
                            () -> {
                                recovering.countDown();
                                service.recoverStaleClaims();
                            });
            var duplicate = pool.submit(() -> service.deliver(claim));
            try {
                assertThat(recovering.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> recovery.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            recovery.get(15, TimeUnit.SECONDS);
            duplicate.get(15, TimeUnit.SECONDS);
        }
        verify(sender, times(1)).send(any(MimeMessage.class));
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(service.claimReadyMessages()).isEmpty();
    }

    private Long pendingMessage(TransactionTemplate tx) {
        return tx.execute(
                status -> {
                    var now = LocalDateTime.now(clock);
                    var user = new User();
                    user.setEmail("outbox-" + UUID.randomUUID() + "@test.local");
                    user.setFullName("Outbox recipient");
                    user.setStatus(UserStatus.ACTIVE);
                    user.setPasswordHash("unused");
                    user.setCreatedAt(now);
                    user.setUpdatedAt(now);
                    users.saveAndFlush(user);
                    var message = new EmailOutbox();
                    message.setEventType(EmailNotificationEventType.TASK_ASSIGNED);
                    message.setEventKey("audit-" + UUID.randomUUID());
                    message.setRecipientUser(user);
                    message.setRecipientEmail(user.getEmail());
                    message.setRecipientName(user.getFullName());
                    message.setSenderName("Test");
                    message.setFromEmail("thongbao@test.local");
                    message.setSubject("Concurrency probe");
                    message.setBody("No sensitive data");
                    message.setStatus(EmailOutboxStatus.PENDING);
                    message.setNextAttemptAt(now);
                    message.setResolvedScopeType(NotificationScopeType.SYSTEM);
                    message.setResolvedScopeId(0L);
                    return outbox.saveAndFlush(message).getId();
                });
    }
}
