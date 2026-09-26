package com.merchtyl.cash;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CashLedgerRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CashLedgerRepository cashLedgerRepository;

    @Test
    void lotteryCashPayoutSumAcceptsOmittedDateFiltersOnPostgres() {
        BigDecimal total = cashLedgerRepository.sumLotteryCashPayouts(
                UUID.randomUUID(), null, null, null, null, null);

        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    void lotteryCashPayoutSumAcceptsBoundedDateFiltersOnPostgres() {
        BigDecimal total = cashLedgerRepository.sumLotteryCashPayouts(
                UUID.randomUUID(), null, null, null,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-24"));

        assertThat(total).isEqualByComparingTo("0.00");
    }
}
