package com.merchtyl.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class InvoiceHistoryQueryRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    InvoiceHistoryQueryRepository repository;

    @Test
    void postgresHistoryQueriesExecuteForOwnerAndAssignedStoreScopes() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        assertThat(repository.count(tenantId, Set.of(), true, null, "")).isZero();
        assertThat(repository.find(tenantId, Set.of(), true, null, "", 0, 10)).isEmpty();
        assertThat(repository.count(tenantId, Set.of(storeId), false, storeId, "INV-102")).isZero();
        assertThat(repository.find(tenantId, Set.of(storeId), false, storeId, "INV-102", 0, 10)).isEmpty();
    }
}
