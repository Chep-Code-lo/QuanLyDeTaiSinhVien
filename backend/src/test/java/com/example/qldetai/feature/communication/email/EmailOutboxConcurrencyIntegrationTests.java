package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
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
    @MockitoBean JavaMailSender sender;

    @Test
    void auditReproducesDuplicateSmtpSendAfterStaleClaimRecovery() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> {
            var now = LocalDateTime.now(clock);
            var user = new User();
            user.setEmail("outbox-" + UUID.randomUUID() + "@test.local");
            user.setFullName("Outbox recipient"); user.setStatus(UserStatus.ACTIVE);
            user.setPasswordHash("unused"); user.setCreatedAt(now); user.setUpdatedAt(now);
            users.saveAndFlush(user);
            var message = new EmailOutbox();
            message.setEventType(EmailNotificationEventType.TASK_ASSIGNED);
            message.setEventKey("audit-" + UUID.randomUUID()); message.setRecipientUser(user);
            message.setRecipientEmail(user.getEmail()); message.setRecipientName(user.getFullName());
            message.setSenderName("Test"); message.setFromEmail("thongbao@test.local");
            message.setSubject("Concurrency probe"); message.setBody("No sensitive data");
            message.setStatus(EmailOutboxStatus.PENDING); message.setNextAttemptAt(now);
            message.setResolvedScopeType(NotificationScopeType.SYSTEM); message.setResolvedScopeId(0L);
            return outbox.saveAndFlush(message).getId();
        });
        assertThat(service.claimReadyMessages()).containsExactly(id);
        var sends = new CyclicBarrier(2);
        var firstSending = new CountDownLatch(1);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> { firstSending.countDown(); sends.await(10, TimeUnit.SECONDS); return null; })
                .when(sender).send(any(MimeMessage.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.deliver(id));
            assertThat(firstSending.await(10, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> outbox.findById(id).orElseThrow()
                    .setClaimedAt(LocalDateTime.now(clock).minusMinutes(16)));
            service.recoverStaleClaims();
            assertThat(service.claimReadyMessages()).containsExactly(id);
            var second = pool.submit(() -> service.deliver(id));
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        // Audit reproducer: after a claim-token/fencing fix only the claim owner may send.
        verify(sender, times(2)).send(any(MimeMessage.class));
        assertThat(outbox.findById(id).orElseThrow().getStatus()).isEqualTo(EmailOutboxStatus.SENT);
    }
}
