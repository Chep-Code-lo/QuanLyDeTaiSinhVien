package com.example.qldetai.common.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** Logs only slow service calls so normal request logging remains useful. */
@Aspect
@Component
@Slf4j
public class SlowServiceCallLoggingAspect {

    private static final long SLOW_CALL_THRESHOLD_MS = 500;

    @Around("execution(public * com.example.qldetai.feature..*Service.*(..))")
    public Object logSlowCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (durationMs >= SLOW_CALL_THRESHOLD_MS) {
                log.warn(
                        "slowServiceCall service={} durationMs={}",
                        joinPoint.getSignature().toShortString(),
                        durationMs);
            }
        }
    }
}
