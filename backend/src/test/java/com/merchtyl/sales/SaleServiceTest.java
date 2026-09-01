package com.merchtyl.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerEntryCommand;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashLedgerSourceType;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.idempotency.IdempotencyOperationResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyState;
import com.merchtyl.inventory.InventoryService;
import com.merchtyl.inventory.InventoryStockChangeRequest;
import com.merchtyl.inventory.InventoryTransactionResponse;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.tax.IncludedPriceBehavior;
import com.merchtyl.tax.TaxCalculationRequest;
import com.merchtyl.tax.TaxCalculationResponse;
import com.merchtyl.tax.TaxEngine;
import com.merchtyl.tax.TaxRoundingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000905");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final RegisterSessionRepository registerSessionRepository = mock(RegisterSessionRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SaleItemHandlerRegistry saleItemHandlerRegistry = mock(SaleItemHandlerRegistry.class);
    private final TaxEngine taxEngine = mock(TaxEngine.class);
    private final AuditService auditService = mock(AuditService.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final CashLedgerService cashLedgerService = mock(CashLedgerService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TransactionOperations transactions = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };
    private final Store store = mock(Store.class);
    private final Register register = mock(Register.class);
    private final RegisterSession registerSession = mock(RegisterSession.class);
    private final User cashier = new User("cashier@example.test", "Cashier One", "hash");
    private final User otherCashier = mock(User.class);
    private final Product product = new Product(new ProductValues(
            "SKU-1",
            "Coffee",
            null,
            SellableType.STANDARD_PRODUCT,
            null,
            new BigDecimal("1.0000"),
            new BigDecimal("5.0000"),
            null,
            null,
            true,
            true,
            false,
            null,
            null,
            List.of(),
            List.of(),
            Set.of(ProductCapability.ALLOW_DISCOUNT)));
    private final SaleService service = new SaleService(
            saleRepository,
            registerSessionRepository,
            productRepository,
            userRepository,
            saleItemHandlerRegistry,
            taxEngine,
            auditService,
            idempotencyService,
            objectMapper,
            inventoryService,
            cashLedgerService,
            transactions,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(store.getId()).thenReturn(STORE_ID);
        when(store.getTimezone()).thenReturn("America/Los_Angeles");
        when(store.getCurrencyCode()).thenReturn("USD");
        when(store.isPricesIncludeTax()).thenReturn(false);
        when(register.getId()).thenReturn(REGISTER_ID);
        when(registerSession.getId()).thenReturn(SESSION_ID);
        when(registerSession.getStore()).thenReturn(store);
        when(registerSession.getRegister()).thenReturn(register);
        when(registerSession.getAssignedCashier()).thenReturn(cashier);
        when(registerSession.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(otherCashier.getId()).thenReturn(OTHER_USER_ID);
        when(otherCashier.isEnabled()).thenReturn(true);
        when(otherCashier.isLocked()).thenReturn(false);
        when(registerSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(registerSession));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("cashier@example.test")).thenReturn(Optional.of(cashier));
        when(userRepository.findByEmailIgnoreCase("other@example.test")).thenReturn(Optional.of(otherCashier));
        when(saleRepository.saveAndFlush(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taxEngine.calculate(any(TaxCalculationRequest.class), any())).thenReturn(taxResponse(new BigDecimal("10.00"), new BigDecimal("1.50"), new BigDecimal("11.50")));
    }

    @Test
    void createDraftUsesOpenRegisterSessionAndAudits() {
        SaleResponse response = service.createDraft(new SaleCreateDraftRequest(SESSION_ID, null, " pos "), cashierAuth());

        assertThat(response.status()).isEqualTo(SaleStatus.DRAFT);
        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.registerId()).isEqualTo(REGISTER_ID);
        assertThat(response.registerSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.createdBy()).isEqualTo(cashier.getId());
        assertThat(response.businessDate()).isEqualTo(LocalDate.parse("2026-07-27"));
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.saleChannel()).isEqualTo("pos");
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void cashierCannotCreateDraftForSomeoneElsesSession() {
        when(registerSession.getAssignedCashier()).thenReturn(otherCashier);

        assertThatThrownBy(() -> service.createDraft(new SaleCreateDraftRequest(SESSION_ID, null, null), cashierAuth()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Sale user must be assigned to this register session");

        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void addItemUsesHandlerRegistryAndRecalculatesTax() {
        Sale sale = draftSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        SaleResponse response = service.addItem(sale.getId(), addItemRequest(new BigDecimal("2.0000")), cashierAuth());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().productName()).isEqualTo("Coffee");
        assertThat(response.items().getFirst().lineSubtotal()).isEqualByComparingTo("10.00");
        assertThat(response.items().getFirst().estimatedTaxAmount()).isEqualByComparingTo("1.50");
        assertThat(response.totalAmount()).isEqualByComparingTo("11.50");
        verify(saleItemHandlerRegistry, org.mockito.Mockito.times(2)).validate(any(SaleItemRequest.class));
        verify(taxEngine).calculate(any(TaxCalculationRequest.class), any());
    }

    @Test
    void scanningSameSellableTwiceIncrementsExistingLine() {
        Sale sale = draftSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        service.addItem(sale.getId(), addItemRequest(BigDecimal.ONE), cashierAuth());
        SaleResponse response = service.addItem(sale.getId(), addItemRequest(BigDecimal.ONE), cashierAuth());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualByComparingTo("2.0000");
    }

    @Test
    void updateQuantityRecalculatesExistingItem() {
        Sale sale = draftSale();
        SaleItem item = saleItem(sale, new BigDecimal("1.0000"));
        sale.addItem(item);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(taxEngine.calculate(any(TaxCalculationRequest.class), any())).thenReturn(taxResponse(new BigDecimal("15.00"), new BigDecimal("2.25"), new BigDecimal("17.25")));

        SaleResponse response = service.updateQuantity(sale.getId(), item.getId(), new SaleUpdateQuantityRequest(new BigDecimal("3.0000")), cashierAuth());

        assertThat(response.items().getFirst().quantity()).isEqualByComparingTo("3.0000");
        assertThat(response.subtotalAmount()).isEqualByComparingTo("15.00");
        assertThat(response.estimatedTaxAmount()).isEqualByComparingTo("2.25");
        assertThat(response.totalAmount()).isEqualByComparingTo("17.25");
    }

    @Test
    void removeItemResequencesAndRecalculates() {
        Sale sale = draftSale();
        SaleItem first = saleItem(sale, new BigDecimal("1.0000"));
        SaleItem second = saleItem(sale, new BigDecimal("1.0000"));
        sale.addItem(first);
        sale.addItem(second);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        SaleResponse response = service.removeItem(sale.getId(), first.getId(), cashierAuth());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(second.getId());
        assertThat(response.items().getFirst().lineNumber()).isEqualTo(1);
    }

    @Test
    void holdResumeAndCancelLifecycle() {
        Sale sale = draftSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        SaleResponse held = service.hold(sale.getId(), cashierAuth());
        assertThat(held.status()).isEqualTo(SaleStatus.HELD);
        assertThat(held.heldAt()).isEqualTo(NOW);

        SaleResponse resumed = service.resume(sale.getId(), cashierAuth());
        assertThat(resumed.status()).isEqualTo(SaleStatus.DRAFT);
        assertThat(resumed.heldAt()).isNull();

        SaleResponse cancelled = service.cancel(sale.getId(), cashierAuth());
        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void cannotAddItemToHeldSale() {
        Sale sale = draftSale();
        sale.hold(NOW);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.addItem(sale.getId(), addItemRequest(BigDecimal.ONE), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sale must be in draft status");
    }

    @Test
    void splitPaymentsCalculatePaidBalanceAndCashChange() {
        Sale sale = payableSale();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));

        SaleResponse debit = service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.DEBIT,
                new BigDecimal("5.00"),
                null,
                "terminal-123",
                "Manual debit"), cashierAuth());

        assertThat(debit.payments()).hasSize(1);
        assertThat(debit.payments().getFirst().method()).isEqualTo(PaymentMethod.DEBIT);
        assertThat(debit.paidAmount()).isEqualByComparingTo("5.00");
        assertThat(debit.balanceDue()).isEqualByComparingTo("6.50");
        assertThat(debit.changeDue()).isEqualByComparingTo("0.00");
        assertThat(debit.paymentComplete()).isFalse();

        SaleResponse cash = service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH,
                new BigDecimal("6.50"),
                new BigDecimal("10.00"),
                null,
                null), cashierAuth());

        assertThat(cash.payments()).hasSize(2);
        assertThat(cash.payments().get(1).cashTendered()).isEqualByComparingTo("10.00");
        assertThat(cash.payments().get(1).changeDue()).isEqualByComparingTo("3.50");
        assertThat(cash.paidAmount()).isEqualByComparingTo("11.50");
        assertThat(cash.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(cash.changeDue()).isEqualByComparingTo("3.50");
        assertThat(cash.paymentComplete()).isTrue();
        verify(auditService, org.mockito.Mockito.times(2)).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void cashTenThenDebitThirtyPaysFortyDollarSale() {
        Sale sale = payableSale();
        sale.getItems().getFirst().setCalculatedAmounts(new BigDecimal("40.00"), BigDecimal.ZERO.setScale(2), new BigDecimal("40.00"));
        sale.setTotals(new BigDecimal("40.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), new BigDecimal("40.00"));
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));

        SaleResponse cash = service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH, new BigDecimal("10.00"), new BigDecimal("10.00"), null, null), cashierAuth());

        assertThat(cash.paidAmount()).isEqualByComparingTo("10.00");
        assertThat(cash.balanceDue()).isEqualByComparingTo("30.00");
        assertThat(cash.paymentComplete()).isFalse();

        SaleResponse debit = service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.DEBIT, new BigDecimal("30.00"), null, "terminal-456", null), cashierAuth());

        assertThat(debit.payments()).extracting(PaymentResponse::method, PaymentResponse::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PaymentMethod.CASH, new BigDecimal("10.00")),
                        org.assertj.core.groups.Tuple.tuple(PaymentMethod.DEBIT, new BigDecimal("30.00")));
        assertThat(debit.paidAmount()).isEqualByComparingTo("40.00");
        assertThat(debit.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(debit.paymentComplete()).isTrue();
    }

    @Test
    void paymentValidationRejectsOverpayInsufficientCashAndMissingCardReference() {
        Sale sale = payableSale();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CREDIT,
                new BigDecimal("12.00"),
                null,
                "auth-1",
                null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("amount cannot exceed remaining balance due");

        assertThatThrownBy(() -> service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH,
                new BigDecimal("5.00"),
                new BigDecimal("4.99"),
                null,
                null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("cashTendered must be greater than or equal to amount");

        assertThatThrownBy(() -> service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.DEBIT,
                new BigDecimal("5.00"),
                null,
                null,
                null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("reference is required for manual debit and credit payments");
    }

    @Test
    void cartCannotChangeAfterCompletedPaymentIsRecorded() {
        Sale sale = payableSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH,
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                null,
                null), cashierAuth());

        assertThatThrownBy(() -> service.addItem(sale.getId(), addItemRequest(BigDecimal.ONE), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sale payments are immutable; cart cannot be changed after payment is recorded");
    }

    @Test
    void completeRecalculatesTaxDeductsInventoryWritesCashLedgerAndSnapshotsItems() {
        Sale sale = payableSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        InventoryTransactionResponse inventoryResponse = inventoryResponse(sale);
        when(inventoryService.recordStockChange(any(InventoryStockChangeRequest.class), any()))
                .thenReturn(inventoryResponse);
        service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH,
                new BigDecimal("11.50"),
                new BigDecimal("20.00"),
                null,
                null), cashierAuth());

        SaleResponse response = service.complete(sale.getId(), cashier, cashierAuth());

        assertThat(response.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(response.completedBy()).isEqualTo(cashier.getId());
        assertThat(response.completedAt()).isEqualTo(NOW);
        assertThat(response.paymentComplete()).isTrue();
        assertThat(response.items().getFirst().completedProductCost()).isEqualByComparingTo("1.0000");
        assertThat(response.items().getFirst().completedProductPrice()).isEqualByComparingTo("5.0000");
        assertThat(response.items().getFirst().completedProductCapabilities()).contains("TRACK_INVENTORY");

        ArgumentCaptor<InventoryStockChangeRequest> stockChange = ArgumentCaptor.forClass(InventoryStockChangeRequest.class);
        verify(inventoryService).recordStockChange(stockChange.capture(), any());
        assertThat(stockChange.getValue().transactionType()).isEqualTo(InventoryTransactionType.SALE);
        assertThat(stockChange.getValue().quantityDelta()).isEqualByComparingTo("-2.0000");
        assertThat(stockChange.getValue().referenceId()).isEqualTo(sale.getId());

        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedgerService, org.mockito.Mockito.times(2)).append(ledger.capture());
        assertThat(ledger.getAllValues()).extracting(CashLedgerEntryCommand::sourceType)
                .containsExactly(CashLedgerSourceType.SALE_CASH_RECEIPT, CashLedgerSourceType.SALE_CHANGE_GIVEN);
        assertThat(ledger.getAllValues()).extracting(CashLedgerEntryCommand::direction)
                .containsExactly(CashLedgerDirection.IN, CashLedgerDirection.OUT);
        assertThat(ledger.getAllValues().get(0).amount()).isEqualByComparingTo("20.00");
        assertThat(ledger.getAllValues().get(1).amount()).isEqualByComparingTo("8.50");
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void completeRejectsInsufficientPaymentAfterAuthoritativeTaxRecalculation() {
        Sale sale = payableSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH,
                new BigDecimal("11.50"),
                new BigDecimal("11.50"),
                null,
                null), cashierAuth());
        when(taxEngine.calculate(any(TaxCalculationRequest.class), any()))
                .thenReturn(taxResponse(new BigDecimal("10.00"), new BigDecimal("2.00"), new BigDecimal("12.00")));

        assertThatThrownBy(() -> service.complete(sale.getId(), cashier, cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sale has insufficient payments");

        verify(inventoryService, never()).recordStockChange(any(), any());
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void completeDoesNotWriteLedgerOrAuditWhenInventoryDeductionFails() {
        Sale sale = payableSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        service.recordPayment(sale.getId(), new SalePaymentRequest(
                PaymentMethod.CASH,
                new BigDecimal("11.50"),
                new BigDecimal("11.50"),
                null,
                null), cashierAuth());
        when(inventoryService.recordStockChange(any(InventoryStockChangeRequest.class), any()))
                .thenThrow(new ConflictException("Store does not allow negative stock"));

        assertThatThrownBy(() -> service.complete(sale.getId(), cashier, cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Store does not allow negative stock");

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.DRAFT);
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void completeIdempotentlyDelegatesToIdempotencyServiceWithSaleScopedRequest() {
        IdempotencyResult expected = new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                "application/json",
                "{\"id\":\"sale\"}",
                false);
        when(idempotencyService.execute(any(), any(), any(), any(), any())).thenReturn(expected);
        Sale sale = draftSale();
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        IdempotencyResult result = service.completeIdempotently(sale.getId(), "complete-key", cashierAuth());

        assertThat(result).isEqualTo(expected);
        verify(idempotencyService).execute(
                org.mockito.Mockito.eq(cashier.getId()),
                org.mockito.Mockito.eq("POST /api/v1/sales/{id}/complete"),
                org.mockito.Mockito.eq("complete-key"),
                org.mockito.Mockito.contains(sale.getId().toString()),
                any());
    }

    private Sale draftSale() {
        return new Sale(
                store,
                register,
                registerSession,
                cashier,
                null,
                LocalDate.parse("2026-07-27"),
                "POS",
                "USD",
                false);
    }

    private Sale payableSale() {
        Sale sale = draftSale();
        SaleItem item = saleItem(sale, new BigDecimal("2.0000"));
        item.setCalculatedAmounts(new BigDecimal("10.00"), new BigDecimal("1.50"), new BigDecimal("11.50"));
        sale.addItem(item);
        sale.setTotals(
                new BigDecimal("10.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("1.50"),
                new BigDecimal("11.50"));
        return sale;
    }

    private SaleItem saleItem(Sale sale, BigDecimal quantity) {
        return new SaleItem(
                sale,
                product,
                quantity,
                new BigDecimal("5.00"),
                BigDecimal.ZERO.setScale(2),
                false,
                false,
                null,
                null,
                null,
                null);
    }

    private SaleAddItemRequest addItemRequest(BigDecimal quantity) {
        return new SaleAddItemRequest(
                product.getId(),
                quantity,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static TaxCalculationResponse taxResponse(BigDecimal net, BigDecimal tax, BigDecimal gross) {
        return new TaxCalculationResponse(
                STORE_ID,
                null,
                null,
                PRODUCT_ID,
                null,
                LocalDate.parse("2026-07-27"),
                "POS",
                "USD",
                BigDecimal.ONE,
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                false,
                net,
                tax,
                gross,
                false,
                false,
                false,
                IncludedPriceBehavior.USE_RATE_SETTING,
                TaxRoundingStrategy.HALF_UP,
                List.of(),
                List.of(),
                null);
    }

    private static InventoryTransactionResponse inventoryResponse(Sale sale) {
        return new InventoryTransactionResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000930"),
                UUID.fromString("00000000-0000-0000-0000-000000000931"),
                sale.getStore().getId(),
                sale.getItems().getFirst().getProduct().getId(),
                InventoryTransactionType.SALE,
                new BigDecimal("-2.0000"),
                new BigDecimal("8.0000"),
                "SALE",
                sale.getId(),
                "Coffee",
                sale.getCreatedBy().getId(),
                NOW,
                NOW,
                0);
    }

    private static UsernamePasswordAuthenticationToken cashierAuth() {
        return new UsernamePasswordAuthenticationToken(
                "cashier@example.test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }
}
