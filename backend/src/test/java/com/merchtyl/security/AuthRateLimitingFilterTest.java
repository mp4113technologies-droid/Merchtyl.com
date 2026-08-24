package com.merchtyl.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimitingFilterTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void limitsRepeatedAuthenticationAttemptsByRemoteAddressAndPath() throws Exception {
        AuthRateLimitingFilter filter = new AuthRateLimitingFilter(
                new SecurityProperties(
                        new SecurityProperties.Cors(List.of()),
                        new SecurityProperties.RateLimit(true, 2, Duration.ofMinutes(1)),
                        null),
                objectMapper,
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("/api/v1/auth/login"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("/api/v1/auth/login"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/login"), limited, chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString())
                .contains("\"code\":\"rate_limited\"")
                .doesNotContain("password")
                .doesNotContain("token");
    }

    @Test
    void ignoresNonAuthPaths() throws Exception {
        AuthRateLimitingFilter filter = new AuthRateLimitingFilter(
                new SecurityProperties(
                        new SecurityProperties.Cors(List.of()),
                        new SecurityProperties.RateLimit(true, 1, Duration.ofMinutes(1)),
                        null),
                objectMapper,
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("/api/v1/products"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("/api/v1/products"), new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void boundsTrackedRemoteAddressesDuringDistributedTraffic() throws Exception {
        AuthRateLimitingFilter filter = new AuthRateLimitingFilter(
                new SecurityProperties(
                        new SecurityProperties.Cors(List.of()),
                        new SecurityProperties.RateLimit(true, 20, Duration.ofMinutes(1)),
                        null),
                objectMapper,
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int index = 0; index < 10_100; index++) {
            MockHttpServletRequest request = request("/api/v1/auth/login");
            request.setRemoteAddr("203.0." + (index / 256) + "." + (index % 256));
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        Field bucketsField = AuthRateLimitingFilter.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        Map<?, ?> buckets = (Map<?, ?>) bucketsField.get(filter);
        assertThat(buckets).hasSizeLessThanOrEqualTo(10_001);
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
