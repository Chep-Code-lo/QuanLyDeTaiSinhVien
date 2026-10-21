package com.example.qldetai.common.audit;

import com.example.qldetai.feature.communication.domain.AuditLog;
import com.example.qldetai.feature.communication.domain.AuditOutcome;
import com.example.qldetai.feature.communication.repository.AuditLogRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

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
        auditLogRepository.save(log);
    }
}
