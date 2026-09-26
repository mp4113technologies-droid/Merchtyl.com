package com.merchtyl.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SalesClassificationIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SalesClassificationService salesClassificationService;

    @Test
    void postgresAggregationQueriesExecuteAndReturnZeroForAnEmptyScopedSession() {
        UUID sessionId = UUID.randomUUID();

        Map<UUID, SalesClassification> result = salesClassificationService.byRegisterSessions(
                UUID.randomUUID(), List.of(sessionId));

        assertThat(result).containsOnlyKeys(sessionId);
        assertThat(result.get(sessionId)).isEqualTo(SalesClassification.zero());
    }
}
