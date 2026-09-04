package com.merchtyl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "merchtyl.security.cors")
public record CorsProperties(String allowedOrigins, String allowedOriginPatterns) {
    public List<String> exactOrigins() {
        return split(allowedOrigins);
    }

    public List<String> originPatterns() {
        return split(allowedOriginPatterns);
    }

    private static List<String> split(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
