package com.example.qldetai.common.audit;

import com.example.qldetai.config.RequestIdFilter;
import com.example.qldetai.feature.communication.domain.AuditOutcome;
import com.example.qldetai.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class BusinessAuditAspect {

    private final AuditLogWriter writer;

    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint joinPoint, BusinessAudit audit) throws Throwable {
        long startedAt = System.nanoTime();
        Long actorId = currentActorId();
        Long entityId = entityId(joinPoint.getArgs(), audit.entityIdArgument());
        String requestId = currentRequestId();
        try {
            Object result = joinPoint.proceed();
            long durationMs = elapsedMillis(startedAt);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        safeWrite(
                                actorId,
                                audit,
                                entityId,
                                status == STATUS_COMMITTED ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE,
                                requestId,
                                durationMs);
                    }
                });
            } else {
                safeWrite(actorId, audit, entityId, AuditOutcome.SUCCESS, requestId, durationMs);
            }
            return result;
        } catch (Throwable throwable) {
            safeWrite(
                    actorId,
                    audit,
                    entityId,
                    AuditOutcome.FAILURE,
                    requestId,
                    elapsedMillis(startedAt));
            throw throwable;
        }
    }

    private void safeWrite(
            Long actorId,
            BusinessAudit audit,
            Long entityId,
            AuditOutcome outcome,
            String requestId,
            long durationMs) {
        try {
            writer.write(actorId, audit.action(), audit.entityType(), entityId, outcome, requestId, durationMs);
        } catch (RuntimeException exception) {
            log.error("Unable to persist business audit log for {}.", audit.action(), exception);
        }
    }

    private Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal.id()
                : null;
    }

    private String currentRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
            if (value instanceof String requestId) {
                return requestId;
            }
        }
        return UUID.randomUUID().toString();
    }

    private Long entityId(Object[] arguments, int index) {
        if (index >= 0 && index < arguments.length && arguments[index] instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
