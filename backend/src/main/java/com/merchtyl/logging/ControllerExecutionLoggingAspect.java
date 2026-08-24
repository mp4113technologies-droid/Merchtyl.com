package com.merchtyl.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@Aspect
@Component
public class ControllerExecutionLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(ControllerExecutionLoggingAspect.class);

    @Around("within(com.merchtyl..*) && @within(restController)")
    public Object logControllerExecution(ProceedingJoinPoint joinPoint, RestController restController) throws Throwable {
        String controller = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String operation = joinPoint.getSignature().getName();
        log.debug("Entering Controller controller={} operation={}", controller, operation);
        long started = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.debug("Leaving Controller controller={} operation={} duration_ms={}", controller, operation, durationMs);
            return result;
        } catch (Throwable exception) {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.warn("Controller failed controller={} operation={} duration_ms={} exception_type={}",
                    controller,
                    operation,
                    durationMs,
                    exception.getClass().getName());
            throw exception;
        }
    }
}
