package com.merchtyl.platform.web;

import com.merchtyl.logging.MerchtylLoggingProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterMemoryTest {
    @Test
    void doesNotBufferResponseBodyForSizeLogging() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(new MerchtylLoggingProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletResponse> downstreamResponse = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/large-response"), response, (request, actualResponse) -> {
            downstreamResponse.set((HttpServletResponse) actualResponse);
            actualResponse.getOutputStream().write(new byte[1024]);
        });

        assertThat(downstreamResponse.get()).isSameAs(response);
        assertThat(response.getContentAsByteArray()).hasSize(1024);
    }
}
