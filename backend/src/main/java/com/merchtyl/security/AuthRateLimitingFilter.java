package com.merchtyl.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.common.ApiError;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.platform.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class AuthRateLimitingFilter extends OncePerRequestFilter {
    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh",
            "/api/v1/auth/first-login/change-password",
            "/api/v1/platform/auth/login",
            "/api/v1/platform/admins/activate",
            "/api/v1/platform/owner-invitations/activate",
            "/api/v1/public/contact");

    private final SecurityProperties.RateLimit properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitingFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this(securityProperties, objectMapper, Clock.systemUTC());
    }

    AuthRateLimitingFilter(SecurityProperties securityProperties, ObjectMapper objectMapper, Clock clock) {
        this.properties = securityProperties.rateLimit();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || !LIMITED_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Instant now = Instant.now(clock);
        cleanExpiredBuckets(now);
        String requestedKey = request.getRemoteAddr() + ':' + request.getServletPath();
        String key = buckets.size() >= CLEANUP_THRESHOLD && !buckets.containsKey(requestedKey)
                ? "overflow:" + request.getServletPath()
                : requestedKey;
        AtomicReference<Bucket> updated = new AtomicReference<>();
        buckets.compute(key, (ignored, current) -> {
            Duration window = windowFor(request.getServletPath());
            Bucket next = current == null || !now.isBefore(current.resetAt())
                    ? new Bucket(now.plus(window), 1)
                    : new Bucket(current.resetAt(), current.count() + 1);
            updated.set(next);
            return next;
        });
        Bucket bucket = updated.get();
        if (bucket.count() <= maxAttemptsFor(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRateLimitResponse(request, response, bucket, now);
    }

    private void writeRateLimitResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            Bucket bucket,
            Instant now) throws IOException {
        long retryAfterSeconds = Math.max(1, Duration.between(now, bucket.resetAt()).toSeconds());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                "rate_limited",
                request.getServletPath().equals("/api/v1/public/contact")
                        ? "Too many contact requests. Please try again later."
                        : "Too many authentication attempts",
                HttpStatus.TOO_MANY_REQUESTS.value(),
                request.getRequestURI(),
                request.getMethod(),
                correlationId,
                List.of(),
                now));
    }

    private int maxAttemptsFor(String path) {
        if (path.equals("/api/v1/public/contact")) return properties.contactMaxAttempts();
        if (isPasswordReset(path)) return properties.passwordResetMaxAttempts();
        return properties.authMaxAttempts();
    }

    private Duration windowFor(String path) {
        if (path.equals("/api/v1/public/contact")) return properties.contactWindow();
        if (isPasswordReset(path)) return properties.passwordResetWindow();
        return properties.authWindow();
    }

    private static boolean isPasswordReset(String path) {
        return path.equals("/api/v1/auth/forgot-password") || path.equals("/api/v1/auth/reset-password");
    }

    private void cleanExpiredBuckets(Instant now) {
        if (buckets.size() < CLEANUP_THRESHOLD) {
            return;
        }
        buckets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetAt()));
    }

    private record Bucket(Instant resetAt, int count) {
    }
}
