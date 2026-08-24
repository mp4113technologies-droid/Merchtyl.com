package com.merchtyl.logging;

import org.slf4j.MDC;

import java.util.List;

public final class LoggingMdc {
    public static final String CORRELATION_ID = "correlationId";
    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    public static final String STORE_ID = "storeId";
    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";
    public static final String REQUEST_URI = "requestUri";
    public static final String HTTP_METHOD = "httpMethod";

    private static final List<String> MERCHTYL_KEYS = List.of(
            CORRELATION_ID,
            REQUEST_ID,
            TENANT_ID,
            STORE_ID,
            USER_ID,
            USERNAME,
            REQUEST_URI,
            HTTP_METHOD);

    private LoggingMdc() {
    }

    public static void putIfNotBlank(String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank()) {
            MDC.put(key, LogSanitizer.clean(text));
        }
    }

    public static void clearRequestContext() {
        MERCHTYL_KEYS.forEach(MDC::remove);
    }
}
