package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class EmailOutboxServiceTests {

    @SuppressWarnings("unchecked")
    @Test
    void smtpFailureUsesMockSenderAndQueuesRetryWithoutThrowing() {
        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        EmailDeliveryLogRepository deliveryLogRepository = mock(EmailDeliveryLogRepository.class);
        com.example.qldetai.feature.identity.repository.UserRepository userRepository = mock(
                com.example.qldetai.feature.identity.repository.UserRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new MailSendException("temporary SMTP outage")).when(sender).send(any(MimeMessage.class));

        EmailNotificationProperties properties = new EmailNotificationProperties();
        properties.setMaxAttempts(4);
        properties.setRetryBaseDelaySeconds(60);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC);
        EmailOutboxDeliveryStateService deliveryStateService = new EmailOutboxDeliveryStateService(
                outboxRepository, deliveryLogRepository, properties, fixedClock);
        EmailOutboxService service = new EmailOutboxService(
                outboxRepository,
                userRepository,
                new EmailTemplateRenderer(),
                senderProvider,
                deliveryStateService,
                properties,
                fixedClock);

        EmailOutbox outbox = sendingOutbox();
        when(outboxRepository.findByIdAndStatus(12L, EmailOutboxStatus.SENDING)).thenReturn(Optional.of(outbox));
        when(outboxRepository.findById(12L)).thenReturn(Optional.of(outbox));

        service.deliver(12L);

        verify(sender).send(any(MimeMessage.class));
        verify(deliveryLogRepository).save(any(EmailDeliveryLog.class));
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 0, 1));
        assertThat(outbox.getLastError()).contains("MailSendException");
    }

    private EmailOutbox sendingOutbox() {
        User recipient = new User();
        recipient.setId(5L);
        recipient.setFullName("Sinh viên kiểm thử");
        recipient.setEmail("student@test.local");
        recipient.setStatus(UserStatus.ACTIVE);

        EmailOutbox outbox = new EmailOutbox();
        outbox.setId(12L);
        outbox.setRecipientUser(recipient);
        outbox.setRecipientEmail(recipient.getEmail());
        outbox.setRecipientName(recipient.getFullName());
        outbox.setSenderName("Hệ thống");
        outbox.setFromEmail("thongbao@test.local");
        outbox.setSubject("Kiểm thử");
        outbox.setBody("<p>Nội dung</p>");
        outbox.setStatus(EmailOutboxStatus.SENDING);
        outbox.setAttemptCount(1);
        return outbox;
    }
}
