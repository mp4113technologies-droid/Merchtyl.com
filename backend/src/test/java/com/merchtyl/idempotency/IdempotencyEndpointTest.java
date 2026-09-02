package com.merchtyl.idempotency;

import com.merchtyl.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IdempotencyEndpointTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyEndpointTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000a01");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestIdempotencyOperationRecorder recorder;

    @BeforeEach
    void resetRecorder() {
        recorder.reset();
    }

    @Test
    void identicalRetryReturnsStoredResponse() throws Exception {
        String firstBody = mockMvc.perform(idempotentPost("same-key", "{\"amount\":100}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.operationCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String retryBody = mockMvc.perform(idempotentPost("same-key", "{ \"amount\" : 100 }"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.operationCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(retryBody).isEqualTo(firstBody);
        assertThat(recorder.operationCount()).isEqualTo(1);
    }

    @Test
    void reuseWithDifferentPayloadIsRejected() throws Exception {
        mockMvc.perform(idempotentPost("payload-key", "{\"amount\":100}"))
                .andExpect(status().isCreated());

        mockMvc.perform(idempotentPost("payload-key", "{\"amount\":101}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_CONFLICT"));

        assertThat(recorder.operationCount()).isEqualTo(1);
    }

    @Test
    void failedResponseIsStoredAndReplayedForIdenticalRetry() throws Exception {
        mockMvc.perform(idempotentPost("failed-key", "{\"amount\":100}").param("status", "500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.operationCount").value(1));

        mockMvc.perform(idempotentPost("failed-key", "{\"amount\":100}").param("status", "201"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.operationCount").value(1));

        assertThat(recorder.operationCount()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesRunOperationOnceAndReplayStoredResponse() throws Exception {
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                calls.add(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return mockMvc.perform(idempotentPost("concurrent-key", "{\"amount\":100}")
                                    .param("delayMs", "75"))
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                });
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            var results = executor.invokeAll(calls);

            List<String> bodies = new ArrayList<>();
            for (var result : results) {
                bodies.add(result.get(5, TimeUnit.SECONDS));
            }

            assertThat(bodies).hasSize(requestCount);
            assertThat(bodies).containsOnly(bodies.getFirst());
            assertThat(recorder.operationCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder idempotentPost(
            String key,
            String body) {
        return post("/api/v1/test/idempotency")
                .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, key)
                .header("X-Test-User-Id", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @Import({
            IdempotencyService.class,
            IdempotencyProperties.class,
            TestIdempotencyController.class,
            TestIdempotencyOperationRecorder.class,
            GlobalExceptionHandler.class,
            TestConfig.class
    })
    static class TestApplication {
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        IdempotencyStore idempotencyStore() {
            return new InMemoryIdempotencyStore();
        }

        @Bean
        TransactionOperations idempotencyTransactionOperations() {
            return new NoOpTransactionOperations();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
