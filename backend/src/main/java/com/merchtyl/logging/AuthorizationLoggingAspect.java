package com.merchtyl.logging;

import com.merchtyl.security.PermissionCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class AuthorizationLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationLoggingAspect.class);

    @AfterReturning(
            pointcut = "execution(public boolean com.merchtyl.security.AuthorizationService.has*(..))",
            returning = "allowed")
    public void logPermissionFailure(JoinPoint joinPoint, boolean allowed) {
        if (allowed) {
            return;
        }
        Authentication authentication = authentication(joinPoint.getArgs());
        log.warn("authorization_failure user={} tenant={} permission={} endpoint={} check={}",
                authentication == null ? "" : authentication.getName(),
                org.slf4j.MDC.get(LoggingMdc.TENANT_ID),
                permissions(joinPoint.getArgs()),
                org.slf4j.MDC.get(LoggingMdc.REQUEST_URI),
                joinPoint.getSignature().getName());
    }

    private static Authentication authentication(Object[] args) {
        return Arrays.stream(args)
                .filter(Authentication.class::isInstance)
                .map(Authentication.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static String permissions(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg instanceof PermissionCode || arg instanceof PermissionCode[])
                .map(arg -> arg instanceof PermissionCode permission
                        ? permission.name()
                        : Arrays.toString((PermissionCode[]) arg))
                .findFirst()
                .orElse("");
    }
}
