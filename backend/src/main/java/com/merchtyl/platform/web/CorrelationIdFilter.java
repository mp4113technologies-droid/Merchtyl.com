package com.merchtyl.platform.web;

import com.merchtyl.logging.LoggingMdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = LoggingMdc.CORRELATION_ID;
    public static final String REQUEST_ID_HEADER_NAME = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        MDC.put(LoggingMdc.REQUEST_ID, requestId);
        LoggingMdc.putIfNotBlank(LoggingMdc.REQUEST_URI, request.getRequestURI());
        LoggingMdc.putIfNotBlank(LoggingMdc.HTTP_METHOD, request.getMethod());
        LoggingMdc.putIfNotBlank(LoggingMdc.TENANT_ID, firstHeader(request, "X-Tenant-ID", "X-TenantId", "TenantId"));
        LoggingMdc.putIfNotBlank(LoggingMdc.STORE_ID, firstHeader(request, "X-Store-ID", "X-StoreId", "StoreId"));
        response.setHeader(HEADER_NAME, correlationId);
        response.setHeader(REQUEST_ID_HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            LoggingMdc.clearRequestContext();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER_NAME);
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return inbound.trim();
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
