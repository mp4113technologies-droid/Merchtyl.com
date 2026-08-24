package com.merchtyl.inventory;

import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class InventoryConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    InventoryService inventoryService;

    @Autowired
    InventoryBalanceRepository balanceRepository;

    @Autowired
    InventoryTransactionRepository transactionRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void concurrentStockChangesUseOptimisticBalanceVersionAndTransactionsRemainImmutable() throws Exception {
        Store store = storeRepository.saveAndFlush(store("MAIN", false));
        Product product = productRepository.saveAndFlush(product("COFFEE-12OZ"));

        inventoryService.recordStockChange(new InventoryStockChangeRequest(
                store.getId(),
                product.getId(),
                InventoryTransactionType.OPENING_STOCK,
                new BigDecimal("10.0000"),
                "COUNT",
                null,
                "Opening count",
                null,
                null), null);
        long startingVersion = inventoryService.currentStock(store.getId(), product.getId()).version();

        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> sale = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                inventoryService.recordStockChange(new InventoryStockChangeRequest(
                        store.getId(),
                        product.getId(),
                        InventoryTransactionType.SALE,
                        new BigDecimal("-1.0000"),
                        "SALE",
                        UUID.randomUUID(),
                        null,
                        null,
                        startingVersion), null);
                return true;
            } catch (ConflictException exception) {
                return false;
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(sale);
            var second = executor.submit(sale);
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(inventoryService.currentStock(store.getId(), product.getId()).quantityOnHand())
                    .isEqualByComparingTo("9.0000");
            assertThat(transactionRepository.findAll()).hasSize(2);
        } finally {
            executor.shutdownNow();
        }

        UUID transactionId = transactionRepository.findAll().getFirst().getId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE inventory_transactions SET reason = ? WHERE id = ?",
                "mutated",
                transactionId))
                .hasMessageContaining("inventory_transactions are immutable");
    }

    private static Product product(String sku) {
        return new Product(new ProductValues(
                sku,
                "House Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                new BigDecimal("3.2500"),
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of(ProductCapability.TRACK_INVENTORY)));
    }

    private static Store store(String code, boolean negativeStockAllowed) throws Exception {
        Constructor<Store> constructor = Store.class.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                code,
                "Main Store",
                null,
                "US",
                "CA",
                "100 Market Street",
                null,
                null,
                "USD",
                "en-US",
                "America/Los_Angeles",
                false,
                negativeStockAllowed,
                true);
    }
}
