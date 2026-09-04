package com.merchtyl.config;

import com.merchtyl.platform.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void corsPolicyUsesExplicitAllowedOriginsAndSafeHeaders() {
        SecurityConfig config = new SecurityConfig();
        CorsConfiguration cors = config.corsConfigurationSource(new SecurityProperties(
                        new SecurityProperties.Cors(List.of(" http://localhost:5173 ", "https://example.com"), List.of("https://*.merchtyl.com")),
                        new SecurityProperties.RateLimit(true, 20, Duration.ofMinutes(1)),
                        null))
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173", "https://example.com");
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("https://*.merchtyl.com");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedHeaders()).contains("Authorization", "Content-Type", "Idempotency-Key", "X-Merchant-Slug");
        assertThat(cors.getExposedHeaders()).contains(CorrelationIdFilter.HEADER_NAME);
        assertThat(cors.checkOrigin("https://adviam.merchtyl.com")).isEqualTo("https://adviam.merchtyl.com");
        assertThat(cors.checkOrigin("https://evil-example.com")).isNull();
    }
}
