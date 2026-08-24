package com.merchtyl.inventory;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAdjustmentServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    private final StockAdjustmentRepository adjustmentRepository = mock(StockAdjustmentRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Store store = mock(Store.class);
    private final Product product = mock(Product.class);
    private final User user = mock(User.class);
    private final StockAdjustmentService adjustmentService = new StockAdjustmentService(
            adjustmentRepository,
            storeRepository,
            productRepository,
            userRepository,
            inventoryService,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(store.getId()).thenReturn(STORE_ID);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(user.getId()).thenReturn(USER_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("manager@example.test")).thenReturn(Optional.of(user));
        when(adjustmentRepository.saveAndFlush(any(StockAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRecordsLinesInventoryTransactionsAndAudit() {
        when(inventoryService.recordStockChange(any(), any())).thenReturn(transactionResponse(new BigDecimal("-2.0000"), new BigDecimal("8.0000")));

        StockAdjustmentResponse response = adjustmentService.create(request(StockAdjustmentType.DAMAGED), authentication());

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.reason()).isEqualTo("Cycle count");
        assertThat(response.approvalStatus()).isEqualTo(StockAdjustmentApprovalStatus.POSTED);
        assertThat(response.approvedByUserId()).isEqualTo(USER_ID);
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().adjustmentType()).isEqualTo(StockAdjustmentType.DAMAGED);
        assertThat(response.lines().getFirst().quantity()).isEqualByComparingTo("2.0000");
        assertThat(response.lines().getFirst().quantityDelta()).isEqualByComparingTo("-2.0000");
        assertThat(response.lines().getFirst().resultingQuantity()).isEqualByComparingTo("8.0000");

        verify(inventoryService).recordStockChange(any(InventoryStockChangeRequest.class), any());
        verify(adjustmentRepository).saveAndFlush(any(StockAdjustment.class));
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createDoesNotAuditWhenInventoryUpdateFails() {
        when(inventoryService.recordStockChange(any(), any())).thenThrow(new ConflictException("Store does not allow negative stock"));

        assertThatThrownBy(() -> adjustmentService.create(request(StockAdjustmentType.EXPIRED), authentication()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Store does not allow negative stock");

        verify(adjustmentRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void createRejectsEmptyLines() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(STORE_ID, "Cycle count", null, null, List.of());

        assertThatThrownBy(() -> adjustmentService.create(request, authentication()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least one adjustment line is required");
    }

    private static StockAdjustmentRequest request(StockAdjustmentType adjustmentType) {
        return new StockAdjustmentRequest(
                STORE_ID,
                " Cycle count ",
                " Counted shelf A ",
                " Approved after count ",
                List.of(new StockAdjustmentLineRequest(PRODUCT_ID, adjustmentType, new BigDecimal("2.0000"), 3L)));
    }

    private static InventoryTransactionResponse transactionResponse(BigDecimal quantityDelta, BigDecimal resultingQuantity) {
        return new InventoryTransactionResponse(
                TRANSACTION_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000905"),
                STORE_ID,
                PRODUCT_ID,
                InventoryTransactionType.DAMAGED,
                quantityDelta,
                resultingQuantity,
                "STOCK_ADJUSTMENT",
                UUID.fromString("00000000-0000-0000-0000-000000000906"),
                "Cycle count",
                USER_ID,
                NOW,
                NOW,
                0);
    }

    private static TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken("manager@example.test", null);
    }
}
