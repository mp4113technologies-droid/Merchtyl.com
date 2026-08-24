package com.merchtyl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseOptimizationIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void optimizationIndexesAndConstraintsArePresent() {
        Set<String> indexes = Set.copyOf(jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                """, String.class));

        assertThat(indexes).contains(
                "idx_sales_report_filters",
                "idx_sales_search_updated_id",
                "idx_cash_movements_search_occurred_id",
                "idx_lottery_payouts_reserved_cash",
                "uq_security_users_email_lower",
                "uq_products_sku_lower",
                "uq_product_barcodes_barcode_lower",
                "uq_refund_payments_refund_original_payment");

        String completedConstraint = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'ck_sales_completed_timestamp'
                """, String.class);

        assertThat(completedConstraint)
                .contains("COMPLETED")
                .contains("PARTIALLY_REFUNDED")
                .contains("REFUNDED");
    }

    @Test
    void representativeQueriesUseOptimizationIndexes() {
        jdbcTemplate.execute("set enable_seqscan = off");
        try {
            String storeId = UUID.randomUUID().toString();
            String registerId = UUID.randomUUID().toString();
            String cashierId = UUID.randomUUID().toString();
            String sessionId = UUID.randomUUID().toString();

            String salesPlan = explain("""
                    select id
                    from sales
                    where store_id = ?::uuid
                      and business_date >= date '2026-01-01'
                      and business_date <= date '2026-01-31'
                      and status in ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                      and register_id = ?::uuid
                      and completed_by = ?::uuid
                    """, storeId, registerId, cashierId);
            assertThat(salesPlan).contains("idx_sales_report_filters");

            String reservedCashPlan = explain("""
                    select sum(amount)
                    from lottery_payouts
                    where register_session_id = ?::uuid
                      and payout_method = 'CASH'
                      and status = 'AUTHORIZED'
                    """, sessionId);
            assertThat(reservedCashPlan).contains("idx_lottery_payouts_reserved_cash");
        } finally {
            jdbcTemplate.execute("set enable_seqscan = on");
        }
    }

    private String explain(String sql, Object... args) {
        List<String> lines = jdbcTemplate.queryForList("explain " + sql, String.class, args);
        return String.join("\n", lines);
    }
}
