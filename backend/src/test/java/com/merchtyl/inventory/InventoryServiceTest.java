package com.merchtyl.inventory;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    private final InventoryBalanceRepository balanceRepository = mock(InventoryBalanceRepository.class);
    private final InventoryTransactionRepository transactionRepository = mock(InventoryTransactionRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Store store = mock(Store.class);
    private final Product product = mock(Product.class);
    private final User actor = mock(User.class);
    private final InventoryService inventoryService = new InventoryService(
            balanceRepository,
            transactionRepository,
            storeRepository,
            productRepository,
            userRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(store.getId()).thenReturn(STORE_ID);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.isInventoryTrackingEnabled()).thenReturn(true);
        when(store.getTenantId()).thenReturn(TENANT_ID);
        when(product.getTenantId()).thenReturn(TENANT_ID);
        when(actor.getTenantId()).thenReturn(TENANT_ID);
        when(userRepository.findByEmailIgnoreCase("cashier@test.local")).thenReturn(Optional.of(actor));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(balanceRepository.saveAndFlush(any(InventoryBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.saveAndFlush(any(InventoryTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void openingStockCreatesBalanceAndImmutableTransactionRecord() {
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        InventoryTransactionResponse response = inventoryService.recordStockChange(request(
                InventoryTransactionType.OPENING_STOCK,
                new BigDecimal("12.5000"),
                null), authentication());

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.transactionType()).isEqualTo(InventoryTransactionType.OPENING_STOCK);
        assertThat(response.quantityDelta()).isEqualByComparingTo("12.5000");
        assertThat(response.resultingQuantity()).isEqualByComparingTo("12.5000");
        assertThat(response.occurredAt()).isEqualTo(NOW);
        verify(balanceRepository).saveAndFlush(any(InventoryBalance.class));
        verify(transactionRepository).saveAndFlush(any(InventoryTransaction.class));
    }

    @Test
    void saleAllowsNegativeStockEvenWhenStoreDisallowsOperationalNegativeAdjustments() {
        when(store.isNegativeStockAllowed()).thenReturn(false);
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(balance(new BigDecimal("2.0000"))));

        InventoryTransactionResponse response = inventoryService.recordStockChange(request(
                InventoryTransactionType.SALE,
                new BigDecimal("-3.0000"),
                0L), new TestingAuthenticationToken("cashier@test.local", "n/a"));

        assertThat(response.resultingQuantity()).isEqualByComparingTo("-1.0000");
        verify(balanceRepository).saveAndFlush(any());
        verify(transactionRepository).saveAndFlush(any());
    }

    @Test
    void saleAllowsNegativeStockWhenStoreAllowsIt() {
        when(store.isNegativeStockAllowed()).thenReturn(true);
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(balance(new BigDecimal("2.0000"))));

        InventoryTransactionResponse response = inventoryService.recordStockChange(request(
                InventoryTransactionType.SALE,
                new BigDecimal("-3.0000"),
                0L), new TestingAuthenticationToken("cashier@test.local", "n/a"));

        assertThat(response.quantityDelta()).isEqualByComparingTo("-3.0000");
        assertThat(response.resultingQuantity()).isEqualByComparingTo("-1.0000");
        verify(transactionRepository).saveAndFlush(any(InventoryTransaction.class));
    }

    @Test
    void staleBalanceVersionIsRejectedBeforeStockChange() {
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(balance(new BigDecimal("5.0000"))));

        assertThatThrownBy(() -> inventoryService.recordStockChange(request(
                InventoryTransactionType.PURCHASE,
                new BigDecimal("1.0000"),
                99L), authentication()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Inventory balance was modified by another transaction");

        verify(balanceRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void optimisticLockFailureIsTranslatedToConflict() {
        InventoryBalance balance = balance(new BigDecimal("5.0000"));
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.of(balance));
        when(balanceRepository.saveAndFlush(balance))
                .thenThrow(new ObjectOptimisticLockingFailureException(InventoryBalance.class, balance.getId()));

        assertThatThrownBy(() -> inventoryService.recordStockChange(request(
                InventoryTransactionType.PURCHASE,
                new BigDecimal("1.0000"),
                0L), authentication()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Inventory balance was modified by another transaction");

        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void transactionTypesEnforceExpectedDirection() {
        assertThatThrownBy(() -> inventoryService.recordStockChange(request(
                InventoryTransactionType.SALE,
                new BigDecimal("1.0000"),
                null), authentication()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("SALE requires a negative quantityDelta");
    }

    @Test
    void currentStockReturnsZeroWhenNoBalanceExists() {
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        InventoryBalanceResponse response = inventoryService.currentStock(STORE_ID, PRODUCT_ID);

        assertThat(response.id()).isNull();
        assertThat(response.quantityOnHand()).isEqualByComparingTo("0.0000");
        assertThat(response.version()).isNull();
    }

    @Test
    void currentStockRequiresExistingStoreAndProduct() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.currentStock(STORE_ID, PRODUCT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Store not found");
    }

    private InventoryStockChangeRequest request(InventoryTransactionType type, BigDecimal quantityDelta, Long version) {
        return new InventoryStockChangeRequest(
                STORE_ID,
                PRODUCT_ID,
                type,
                quantityDelta,
                " purchase_order ",
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                " Counted at receiving ",
                null,
                version);
    }

    private InventoryBalance balance(BigDecimal quantity) {
        return new InventoryBalance(store, product, quantity, NOW);
    }

    private TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken("cashier@test.local", "n/a");
    }
}
