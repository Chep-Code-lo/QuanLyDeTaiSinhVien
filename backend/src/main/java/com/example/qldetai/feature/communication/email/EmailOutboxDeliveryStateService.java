package com.example.qldetai.feature.communication.email;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists worker state in independent transactions. Keeping this work in a
 * separate bean is intentional: calls made by {@link EmailOutboxService}
 * must cross a Spring proxy for {@code REQUIRES_NEW} to take effect.
 */
@Service
@RequiredArgsConstructor
public class EmailOutboxDeliveryStateService {

    private final EmailOutboxRepository outboxRepository;
    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final EmailNotificationProperties properties;
    private final Clock clock;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minusMinutes(Math.max(1, properties.getClaimTimeoutMinutes()));
        int batchSize = Math.max(1, Math.min(properties.getMaxPerRun(), 100));
        List<EmailOutbox> stale = outboxRepository.findByStatusAndClaimedAtBefore(
                EmailOutboxStatus.SENDING, threshold, PageRequest.of(0, batchSize));
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        for (EmailOutbox message : stale) {
            boolean retry = message.getAttemptCount() < maxAttempts;
            message.setStatus(retry ? EmailOutboxStatus.PENDING : EmailOutboxStatus.FAILED);
            message.setClaimedAt(null);
            if (retry) {
                message.setNextAttemptAt(now);
            }
            message.setLastError("Email delivery claim timed out; message was returned to the outbox.");
            recordAttempt(message, EmailOutboxStatus.FAILED, message.getLastError(), now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long outboxId) {
        outboxRepository.findById(outboxId)
                .filter(message -> message.getStatus() == EmailOutboxStatus.SENDING)
                .ifPresent(message -> {
                    LocalDateTime now = LocalDateTime.now(clock);
                    message.setStatus(EmailOutboxStatus.SENT);
                    message.setSentAt(now);
                    message.setClaimedAt(null);
                    message.setLastError(null);
                    recordAttempt(message, EmailOutboxStatus.SENT, null, now);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(Long outboxId, String rawError, boolean retryable) {
        outboxRepository.findById(outboxId)
                .filter(message -> message.getStatus() == EmailOutboxStatus.SENDING)
                .ifPresent(message -> {
                    LocalDateTime now = LocalDateTime.now(clock);
                    String safeError = sanitizeError(rawError);
                    boolean retry = retryable && message.getAttemptCount() < Math.max(1, properties.getMaxAttempts());
                    message.setStatus(retry ? EmailOutboxStatus.PENDING : EmailOutboxStatus.FAILED);
                    message.setClaimedAt(null);
                    message.setLastError(safeError);
                    if (retry) {
                        message.setNextAttemptAt(now.plusSeconds(backoffSeconds(message.getAttemptCount())));
                    }
                    recordAttempt(message, EmailOutboxStatus.FAILED, safeError, now);
                });
    }

    private void recordAttempt(
            EmailOutbox message,
            EmailOutboxStatus status,
            String error,
            LocalDateTime now) {
        EmailDeliveryLog log = new EmailDeliveryLog();
        log.setOutbox(message);
        log.setAttemptNo(message.getAttemptCount());
        log.setStatus(status);
        log.setErrorMessage(error);
        log.setCreatedAt(now);
        deliveryLogRepository.save(log);
    }

    private long backoffSeconds(int attemptCount) {
        int exponent = Math.min(Math.max(0, attemptCount - 1), 8);
        long multiplier = 1L << exponent;
        return Math.min(86400L, Math.max(1L, properties.getRetryBaseDelaySeconds()) * multiplier);
    }

    private String sanitizeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Email delivery failed without a diagnostic message.";
        }
        String sanitized = raw
                .replaceAll("(?i)(password|passwd|api[-_]?key|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[redacted]")
                .replaceAll("(?i)(smtp://)[^@\\s]+@", "$1[redacted]@");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }
}
