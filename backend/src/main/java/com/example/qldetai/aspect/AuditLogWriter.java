package com.example.qldetai.aspect;

import com.example.qldetai.feature.communication.AuditLog;
import com.example.qldetai.feature.communication.AuditLogRepository;
import com.example.qldetai.feature.communication.AuditOutcome;
import com.example.qldetai.feature.identity.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AuditLogWriter(AuditLogRepository auditLogRepository, UserRepository userRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(
            Long actorId,
            String action,
            String entityType,
            Long entityId,
            AuditOutcome outcome,
            String requestId,
            long durationMs) {
        AuditLog log = new AuditLog();
        log.setActor(actorId == null ? null : userRepository.findById(actorId).orElse(null));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOutcome(outcome);
        log.setRequestId(requestId);
        log.setDurationMs(durationMs);
        log.setCreatedAt(LocalDateTime.now(clock));
        auditLogRepository.save(log);
    }
}
