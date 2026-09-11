package com.merchtyl.catalogue;

import com.merchtyl.MerchtylApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MerchtylApplication.class, properties = "merchtyl.platform.bootstrap.enabled=false")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MERCHTYL_RUN_LOCAL_PG", matches = "true")
class MerchantIdentifierGenerationIntegrationTest {
    private static final UUID SWEET_SHOP = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_MERCHANT = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach
    void createMerchants() {
        deleteMerchants();
        insertMerchant(SWEET_SHOP, "SWEET_SHOP", "sweet-shop", "Sweet Shop");
        insertMerchant(OTHER_MERCHANT, "OTHER_MERCHANT", "other-merchant", "Other Merchant");
    }

    @AfterEach
    void deleteMerchants() {
        jdbc.update("delete from tenants where id in (?, ?)", SWEET_SHOP, OTHER_MERCHANT);
    }

    @Test
    void prefixesAndEntitySequencesAreStableScopedAndNeverRewind() {
        assertThat(prefix(SWEET_SHOP)).isEqualTo("SS");
        String deletedCode = code(SWEET_SHOP, "CATEGORY", "CAT");
        assertThat(deletedCode).isEqualTo("SSCAT001");
        UUID deletedCategory = UUID.randomUUID();
        jdbc.update("insert into categories(id, tenant_id, code, name) values (?, ?, ?, 'Deleted')",
                deletedCategory, SWEET_SHOP, deletedCode);
        jdbc.update("delete from categories where id = ?", deletedCategory);
        assertThat(code(SWEET_SHOP, "CATEGORY", "CAT")).isEqualTo("SSCAT002");
        assertThat(code(SWEET_SHOP, "BRAND", "BR")).isEqualTo("SSBR001");
        assertThat(code(OTHER_MERCHANT, "CATEGORY", "CAT")).isEqualTo("OMCAT001");

        jdbc.update("update tenants set display_name = 'Sweet Shop Renamed' where id = ?", SWEET_SHOP);
        assertThat(prefix(SWEET_SHOP)).isEqualTo("SS");
        assertThat(code(SWEET_SHOP, "CATEGORY", "CAT")).isEqualTo("SSCAT003");
    }

    @Test
    void concurrentSkuAllocationsAreUniqueAndMerchantIsolated() throws Exception {
        int allocations = 24;
        CountDownLatch ready = new CountDownLatch(allocations);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(allocations);
        try {
            List<Callable<Long>> work = new ArrayList<>();
            for (int index = 0; index < allocations; index++) {
                work.add(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return nextSequence(SWEET_SHOP, "SKU", "COCA-COLA-500ML");
                });
            }
            var futures = work.stream().map(executor::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Long> allocated = new HashSet<>();
            for (var future : futures) allocated.add(future.get(10, TimeUnit.SECONDS));
            assertThat(allocated).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, allocations).boxed().toList());
            assertThat(nextSequence(OTHER_MERCHANT, "SKU", "COCA-COLA-500ML")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertMerchant(UUID id, String code, String slug, String name) {
        jdbc.update("""
                insert into tenants(id, tenant_code, merchant_slug, legal_name, display_name, status,
                                    country_code, default_currency_code, primary_timezone)
                values (?, ?, ?, ?, ?, 'ACTIVE', 'CA', 'CAD', 'America/St_Johns')
                """, id, code, slug, name + " Inc.", name);
    }

    private String prefix(UUID tenantId) {
        return jdbc.queryForObject("select identifier_prefix from tenants where id = ?", String.class, tenantId);
    }

    private String code(UUID tenantId, String namespace, String marker) {
        return prefix(tenantId) + marker + "%03d".formatted(nextSequence(tenantId, namespace, ""));
    }

    private long nextSequence(UUID tenantId, String namespace, String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select next_tenant_identifier_sequence(?, ?, ?)")) {
            statement.setObject(1, tenantId);
            statement.setString(2, namespace);
            statement.setString(3, key);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
