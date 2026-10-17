package com.example.qldetai.feature.communication.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Single-node worker; DB row locks also protect against overlapping scheduler executions. */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class EmailOutboxWorker {

    private final EmailOutboxService emailOutboxService;

    public EmailOutboxWorker(EmailOutboxService emailOutboxService) {
        this.emailOutboxService = emailOutboxService;
    }

    @Scheduled(fixedDelayString = "${app.email.worker-delay-ms:5000}")
    public void processOutbox() {
        emailOutboxService.recoverStaleClaims();
        emailOutboxService.claimReadyMessages().forEach(emailOutboxService::deliver);
    }
}
