package com.merchtyl.logging;

import jakarta.servlet.http.HttpServletRequest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LogSanitizer {
    public static final String MASK = "********";

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "confirmpassword",
            "temporarypassword",
            "token",
            "jwt",
            "refreshtoken",
            "authorization",
            "apikey",
            "api-key",
            "secret",
            "cvv",
            "cardnumber",
            "cookie",
            "setcookie",
            "session",
            "sessionid");

    private LogSanitizer() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]", "_").trim();
    }

    public static String maskValue(String key, Object value, boolean enabled) {
        if (value == null) {
            return null;
        }
        return enabled && isSensitive(key) ? MASK : clean(String.valueOf(value));
    }

    public static String maskQueryString(String queryString, boolean enabled) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        String[] pairs = queryString.split("&");
        StringBuilder masked = new StringBuilder();
        for (String pair : pairs) {
            if (masked.length() > 0) {
                masked.append('&');
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            String decodedKey = decode(key);
            masked.append(clean(key));
            if (separator >= 0) {
                masked.append('=').append(maskValue(decodedKey, value, enabled));
            }
        }
        return masked.toString();
    }

    public static Map<String, String> maskedHeaders(HttpServletRequest request, boolean enabled) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(clean(name), maskValue(name, request.getHeader(name), enabled));
        }
        return headers;
    }

    public static String maskSensitiveText(String value) {
        if (value == null) {
            return null;
        }
        String masked = value;
        for (String field : SENSITIVE_FIELDS) {
            masked = masked.replaceAll("(?i)(" + java.util.regex.Pattern.quote(field) + "\\s*[=:]\\s*)[^\\s,&}]+", "$1" + MASK);
        }
        masked = masked.replaceAll("\\b(?:\\d[ -]*?){13,19}\\b", MASK);
        return masked;
    }

    public static String sanitizedStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return maskSensitiveText(writer.toString());
    }

    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[_\\s-]", "");
        return SENSITIVE_FIELDS.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("refreshtoken")
                || normalized.contains("apikey")
                || normalized.endsWith("token")
                || normalized.contains("secret")
                || normalized.contains("cardnumber")
                || normalized.contains("cookie")
                || normalized.contains("session");
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }
}
