package com.merchtyl.config;

import com.merchtyl.platform.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void corsPolicyUsesExplicitAllowedOriginsAndSafeHeaders() {
        SecurityConfig config = new SecurityConfig();
        CorsConfiguration cors = config.corsConfigurationSource(new CorsProperties(
                        " http://localhost:5173 ,https://example.com", "https://*.merchtyl.com"))
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

    @Test
    void railwayOriginsAndMerchantPatternAreAcceptedByCorsProcessor() throws Exception {
        CorsConfiguration cors = new SecurityConfig().corsConfigurationSource(new CorsProperties(
                        "https://merchtyl.com,https://www.merchtyl.com,https://platform.merchtyl.com",
                        "https://*.merchtyl.com"))
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/platform/auth/login"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly(
                "https://merchtyl.com", "https://www.merchtyl.com", "https://platform.merchtyl.com");
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("https://*.merchtyl.com");
        for (String origin : List.of("https://merchtyl.com", "https://www.merchtyl.com", "https://platform.merchtyl.com",
                "https://adviam.merchtyl.com", "https://random-merchant.merchtyl.com")) {
            MockHttpServletResponse response = preflight(cors, origin);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
            assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
            assertThat(response.getHeader("Access-Control-Allow-Headers").toLowerCase())
                    .contains("authorization", "content-type", "x-merchant-slug");
        }
        assertThat(preflight(cors, "https://evil-example.com").getHeader("Access-Control-Allow-Origin")).isNull();
    }

    private static MockHttpServletResponse preflight(CorsConfiguration cors, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/platform/auth/login");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type, X-Merchant-Slug");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(cors, request, response);
        return response;
    }
}
