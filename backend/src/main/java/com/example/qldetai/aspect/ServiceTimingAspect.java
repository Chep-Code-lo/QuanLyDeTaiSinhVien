package com.example.qldetai.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ghi thời gian xử lý cross-cutting; không làm lẫn logic nghiệp vụ vào service. */
@Aspect
@Component
public class ServiceTimingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTimingAspect.class);

    @Around(
        "execution(* com.example.qldetai.feature.progress..*Service.*(..)) || execution(* com.example.qldetai.feature.evaluation..*Service.*(..))"
    )
    public Object measure(ProceedingJoinPoint point) throws Throwable {
        long started = System.nanoTime();
        try {
            return point.proceed();
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000;
            LOGGER.info("service={} durationMs={}", point.getSignature().toShortString(), ms);
        }
    }
}
