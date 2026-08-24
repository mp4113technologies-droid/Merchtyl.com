package com.merchtyl.logging;

import jakarta.persistence.OptimisticLockException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RepositoryFailureLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(RepositoryFailureLoggingAspect.class);

    @AfterThrowing(pointcut = "execution(* com.merchtyl..*Repository.*(..))", throwing = "exception")
    public void logRepositoryFailure(JoinPoint joinPoint, Throwable exception) {
        String repository = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String method = joinPoint.getSignature().getName();
        String category = category(exception);
        if (isRecoverableDatabaseFailure(exception)) {
            log.warn("repository_failure category={} repository={} method={} exception_type={}",
                    category,
                    repository,
                    method,
                    exception.getClass().getName());
            return;
        }
        log.error("repository_failure category={} repository={} method={} exception_type={} stack_trace={}",
                category,
                repository,
                method,
                exception.getClass().getName(),
                LogSanitizer.sanitizedStackTrace(exception));
    }

    private static boolean isRecoverableDatabaseFailure(Throwable exception) {
        return exception instanceof ObjectOptimisticLockingFailureException
                || exception instanceof OptimisticLockException
                || exception instanceof DuplicateKeyException
                || exception instanceof DataIntegrityViolationException;
    }

    private static String category(Throwable exception) {
        if (exception instanceof ObjectOptimisticLockingFailureException || exception instanceof OptimisticLockException) {
            return "optimistic_locking_failure";
        }
        if (exception instanceof DuplicateKeyException) {
            return "duplicate_key";
        }
        if (exception instanceof DataIntegrityViolationException) {
            return "constraint_violation";
        }
        if (exception instanceof DataAccessException) {
            return "database_failure";
        }
        return "repository_failure";
    }
}
