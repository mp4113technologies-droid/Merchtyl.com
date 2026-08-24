package com.merchtyl.platform.web;

import com.merchtyl.logging.LogSanitizer;
import com.merchtyl.logging.LoggingMdc;
import com.merchtyl.logging.MerchtylLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    public static final String EXCEPTION_TYPE_ATTRIBUTE = "merchtyl.logging.exceptionType";
    public static final String ERROR_CODE_ATTRIBUTE = "merchtyl.logging.errorCode";

    private final MerchtylLoggingProperties properties;

    public RequestLoggingFilter(MerchtylLoggingProperties properties) {
        this.properties = properties;
    }

    public RequestLoggingFilter() {
        this(new MerchtylLoggingProperties());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        if (properties.getRequest().isEnabled()) {
            logIncomingRequest(request);
        }
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            request.setAttribute(EXCEPTION_TYPE_ATTRIBUTE, exception.getClass().getName());
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            updateAuthenticatedMdc();
            if (properties.getResponse().isEnabled()) {
                logOutgoingResponse(request, response, durationMs);
            }
            if (properties.getPerformance().isEnabled()
                    && durationMs > properties.getPerformance().getSlowRequestThresholdMs()) {
                log.warn(
                        "SLOW REQUEST duration_ms={} method={} uri={} controller={}",
                        durationMs,
                        request.getMethod(),
                        logSafe(request.getRequestURI()),
                        controllerName(request));
            }
        }
    }

    private void logIncomingRequest(HttpServletRequest request) {
        boolean maskSensitive = properties.getMaskSensitive().isEnabled();
        log.info(
                "http_request_started method={} uri={} query_params={} client_ip={} user_agent={} authenticated_user={} tenant={} store={} execution_start={} headers={}",
                request.getMethod(),
                logSafe(request.getRequestURI()),
                LogSanitizer.maskQueryString(request.getQueryString(), maskSensitive),
                clientIp(request),
                LogSanitizer.clean(request.getHeader("User-Agent")),
                authenticatedUsername(),
                firstPresent(MDC.get(LoggingMdc.TENANT_ID), request.getHeader("X-Tenant-ID")),
                firstPresent(MDC.get(LoggingMdc.STORE_ID), request.getHeader("X-Store-ID")),
                Instant.now(),
                LogSanitizer.maskedHeaders(request, maskSensitive));
    }

    private void logOutgoingResponse(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String exceptionType = attribute(request, EXCEPTION_TYPE_ATTRIBUTE);
        String errorCode = attribute(request, ERROR_CODE_ATTRIBUTE);
        if (response.getStatus() >= 500) {
            log.error(
                    "http_response_failure status={} duration_ms={} response_size={} controller={} uri={} correlation_id={} exception_type={} business_error_code={}",
                    response.getStatus(),
                    durationMs,
                    responseSize(response),
                    controllerName(request),
                    logSafe(request.getRequestURI()),
                    MDC.get(CorrelationIdFilter.MDC_KEY),
                    exceptionType,
                    errorCode);
            return;
        }
        if (response.getStatus() >= 400 || exceptionType != null) {
            log.warn(
                    "http_response_failure status={} duration_ms={} response_size={} controller={} uri={} correlation_id={} exception_type={} business_error_code={}",
                    response.getStatus(),
                    durationMs,
                    responseSize(response),
                    controllerName(request),
                    logSafe(request.getRequestURI()),
                    MDC.get(CorrelationIdFilter.MDC_KEY),
                    exceptionType,
                    errorCode);
            return;
        }
        log.info(
                "http_response_completed status={} duration_ms={} response_size={} controller={} uri={} correlation_id={}",
                response.getStatus(),
                durationMs,
                responseSize(response),
                controllerName(request),
                logSafe(request.getRequestURI()),
                MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static long responseSize(HttpServletResponse response) {
        String contentLength = response.getHeader("Content-Length");
        if (contentLength == null || contentLength.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(contentLength);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String controllerName(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName();
        }
        return "";
    }

    private static String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? "" : authentication.getName();
    }

    private static void updateAuthenticatedMdc() {
        LoggingMdc.putIfNotBlank(LoggingMdc.USERNAME, authenticatedUsername());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return LogSanitizer.clean(forwarded.split(",")[0]);
        }
        return LogSanitizer.clean(request.getRemoteAddr());
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : LogSanitizer.clean(String.valueOf(value));
    }

    private static String firstPresent(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : LogSanitizer.clean(second);
    }

    private static String logSafe(String value) {
        return value == null ? "" : LogSanitizer.clean(value);
    }
}
