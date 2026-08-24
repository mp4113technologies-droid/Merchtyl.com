package com.merchtyl.reports;

import com.merchtyl.catalogue.Category;
import com.merchtyl.inventory.InventoryBalance;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.inventory.InventoryTransaction;
import com.merchtyl.inventory.InventoryTransactionRepository;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID COFFEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID SODA_ID = UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID CHIPS_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");

    private final InventoryBalanceRepository balanceRepository = mock(InventoryBalanceRepository.class);
    private final InventoryTransactionRepository transactionRepository = mock(InventoryTransactionRepository.class);
    private final InventoryReportService service = new InventoryReportService(
            balanceRepository,
            transactionRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void summarizesStockValueLowNegativeAdjustmentsDamagedAndExpired() {
        Store store = store();
        Product coffee = product(COFFEE_ID, "COFFEE", "Coffee", "1.25");
        Product soda = product(SODA_ID, "SODA", "Soda", "0.80");
        Product chips = product(CHIPS_ID, "CHIPS", "Chips", "1.50");
        InventoryBalance coffeeBalance = balance(store, coffee, "4.0000");
        InventoryBalance sodaBalance = balance(store, soda, "-2.0000");
        InventoryBalance chipsBalance = balance(store, chips, "12.0000");

        when(balanceRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(coffeeBalance, sodaBalance, chipsBalance));
        when(transactionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(transaction(coffeeBalance, InventoryTransactionType.ADJUSTMENT_INCREASE, "3.0000")))
                .thenReturn(List.of(transaction(sodaBalance, InventoryTransactionType.DAMAGED, "-2.0000")))
                .thenReturn(List.of(transaction(chipsBalance, InventoryTransactionType.EXPIRED, "-1.0000")));

        InventoryReportResponse response = service.summarize(new InventoryReportRequest(
                STORE_ID,
                CATEGORY_ID,
                null,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                new BigDecimal("5")));

        assertThat(response.currentStock()).isEqualByComparingTo("14.0000");
        assertThat(response.inventoryValue()).isEqualByComparingTo("21.40");
        assertThat(response.stockItemCount()).isEqualTo(3);
        assertThat(response.lowStockCount()).isEqualTo(1);
        assertThat(response.negativeStockCount()).isEqualTo(1);
        assertThat(response.adjustmentCount()).isEqualTo(1);
        assertThat(response.damagedCount()).isEqualTo(1);
        assertThat(response.expiredCount()).isEqualTo(1);
        assertThat(response.adjustmentQuantity()).isEqualByComparingTo("3.0000");
        assertThat(response.damagedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(response.expiredQuantity()).isEqualByComparingTo("1.0000");
        assertThat(response.adjustmentValue()).isEqualByComparingTo("3.75");
        assertThat(response.damagedValue()).isEqualByComparingTo("1.60");
        assertThat(response.expiredValue()).isEqualByComparingTo("1.50");
        assertThat(response.lowStockRows()).singleElement().satisfies(row -> {
            assertThat(row.productId()).isEqualTo(COFFEE_ID);
            assertThat(row.inventoryValue()).isEqualByComparingTo("5.00");
        });
        assertThat(response.negativeStockRows()).singleElement().satisfies(row ->
                assertThat(row.productId()).isEqualTo(SODA_ID));
        assertThat(response.generatedAt()).isEqualTo(NOW);
    }

    private static Store store() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        return store;
    }

    private static InventoryBalance balance(Store store, Product product, String quantityOnHand) {
        return new InventoryBalance(
                store,
                product,
                new BigDecimal(quantityOnHand),
                Instant.parse("2026-07-27T12:00:00Z"));
    }

    private static InventoryTransaction transaction(
            InventoryBalance balance,
            InventoryTransactionType type,
            String quantityDelta) {
        return new InventoryTransaction(
                balance,
                type,
                new BigDecimal(quantityDelta),
                balance.getQuantityOnHand().add(new BigDecimal(quantityDelta)),
                "TEST",
                UUID.fromString("00000000-0000-0000-0000-000000000399"),
                "Report test",
                UUID.fromString("00000000-0000-0000-0000-000000000398"),
                Instant.parse("2026-07-27T12:00:00Z"));
    }

    private static Product product(UUID productId, String sku, String name, String cost) {
        Category category = new Category("CAT", "Category", null, true);
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);
        Product product = new Product(new ProductValues(
                sku,
                name,
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal(cost),
                BigDecimal.ZERO,
                category,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of()));
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }
}
