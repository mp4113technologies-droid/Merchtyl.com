package com.merchtyl.refunds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerEntryCommand;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashLedgerSourceType;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.idempotency.IdempotencyRecord;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyStore;
import com.merchtyl.idempotency.IdempotencyProperties;
import com.merchtyl.inventory.InventoryService;
import com.merchtyl.inventory.InventoryStockChangeRequest;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.returns.Return;
import com.merchtyl.returns.ReturnItem;
import com.merchtyl.returns.ReturnRepository;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

class RefundServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T15:00:00Z");

    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final RefundPaymentRepository refundPaymentRepository = mock(RefundPaymentRepository.class);
    private final ReturnRepository returnRepository = mock(ReturnRepository.class);
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final CashLedgerService cashLedgerService = mock(CashLedgerService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RefundProperties properties = new RefundProperties();
    private final User manager = new User("manager@example.test", "Manager One", "hash");
    private final TransactionOperations transactions = new ImmediateTransactions();
    private RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(
                refundRepository,
                refundPaymentRepository,
                returnRepository,
                saleRepository,
                userRepository,
                inventoryService,
                cashLedgerService,
                auditService,
                mock(IdempotencyService.class),
                objectMapper,
                properties,
                transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(userRepository.findByEmailIgnoreCase("manager@example.test")).thenReturn(Optional.of(manager));
        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.saveAndFlush(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundPaymentRepository.refundedAmountForOriginalPayment(any())).thenReturn(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void createsRefundFromReturnSnapshotsAndWritesCompensatingRecords() throws Exception {
        Fixture fixture = fixture(new BigDecimal("1.0000"), new BigDecimal("2.0000"));
        when(returnRepository.findByIdForUpdate(fixture.returnRecord().getId())).thenReturn(Optional.of(fixture.returnRecord()));
        when(saleRepository.findByIdForUpdate(fixture.sale().getId())).thenReturn(Optional.of(fixture.sale()));
        when(refundRepository.refundedQuantityForSaleItem(fixture.saleItem().getId())).thenReturn(new BigDecimal("1.0000"));

        RefundResponse response = service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Customer refund",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.75"), fixture.payment().getId(), null, "drawer")),
                null), manager, managerAuth());

        assertThat(response.returnId()).isEqualTo(fixture.returnRecord().getId());
        assertThat(response.originalSaleId()).isEqualTo(fixture.sale().getId());
        assertThat(response.subtotalAmount()).isEqualByComparingTo("5.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("0.75");
        assertThat(response.totalAmount()).isEqualByComparingTo("5.75");
        assertThat(response.payments()).hasSize(1);
        assertThat(response.payments().getFirst().originalPaymentId()).isEqualTo(fixture.payment().getId());
        assertThat(response.itemTaxes()).hasSize(1);
        assertThat(response.itemTaxes().getFirst().taxableAmount()).isEqualByComparingTo("5.00");
        assertThat(response.itemTaxes().getFirst().taxAmount()).isEqualByComparingTo("0.75");
        assertThat(response.itemTaxes().getFirst().productTaxCategoryId()).isEqualTo(fixture.taxCategoryId());
        assertThat(fixture.sale().getStatus()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);

        ArgumentCaptor<InventoryStockChangeRequest> stockChange = ArgumentCaptor.forClass(InventoryStockChangeRequest.class);
        verify(inventoryService).recordStockChange(stockChange.capture(), any());
        assertThat(stockChange.getValue().transactionType()).isEqualTo(InventoryTransactionType.RETURN);
        assertThat(stockChange.getValue().quantityDelta()).isEqualByComparingTo("1.0000");
        assertThat(stockChange.getValue().referenceType()).isEqualTo("REFUND");
        assertThat(stockChange.getValue().referenceId()).isEqualTo(response.id());

        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedgerService).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.CASH_REFUND);
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("5.75");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.REFUND_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("REFUND");
    }

    @Test
    void marksSaleRefundedWhenAllPurchasedQuantityHasBeenRefunded() throws Exception {
        Fixture fixture = fixture(new BigDecimal("2.0000"), new BigDecimal("2.0000"));
        when(returnRepository.findByIdForUpdate(fixture.returnRecord().getId())).thenReturn(Optional.of(fixture.returnRecord()));
        when(saleRepository.findByIdForUpdate(fixture.sale().getId())).thenReturn(Optional.of(fixture.sale()));
        when(refundRepository.refundedQuantityForSaleItem(fixture.saleItem().getId())).thenReturn(new BigDecimal("2.0000"));

        service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Full refund",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("11.50"), fixture.payment().getId(), null, null)),
                null), manager, managerAuth());

        assertThat(fixture.sale().getStatus()).isEqualTo(SaleStatus.REFUNDED);
    }

    @Test
    void requiresApprovalWhenConfigured() throws Exception {
        properties.setApprovalRequired(true);
        Fixture fixture = fixture(new BigDecimal("1.0000"), new BigDecimal("2.0000"));
        when(returnRepository.findByIdForUpdate(fixture.returnRecord().getId())).thenReturn(Optional.of(fixture.returnRecord()));
        when(saleRepository.findByIdForUpdate(fixture.sale().getId())).thenReturn(Optional.of(fixture.sale()));

        assertThatThrownBy(() -> service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Needs approval",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.75"), null, null, null)),
                null), manager, noApprovalAuth()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Refund requires approval");

        RefundResponse response = service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Approved",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.75"), null, null, null)),
                "manager approved"), manager, approvalAuth());

        assertThat(response.approvedBy()).isEqualTo(manager.getId());
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.approvalNotes()).isEqualTo("manager approved");
    }

    @Test
    void rejectsAlreadyRefundedReturnAndPaymentMismatch() throws Exception {
        Fixture fixture = fixture(new BigDecimal("1.0000"), new BigDecimal("2.0000"));
        when(returnRepository.findByIdForUpdate(fixture.returnRecord().getId())).thenReturn(Optional.of(fixture.returnRecord()));
        when(saleRepository.findByIdForUpdate(fixture.sale().getId())).thenReturn(Optional.of(fixture.sale()));
        when(refundRepository.existsByReturnRecord_Id(fixture.returnRecord().getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Duplicate",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.75"), null, null, null)),
                null), manager, managerAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Return has already been refunded");

        when(refundRepository.existsByReturnRecord_Id(fixture.returnRecord().getId())).thenReturn(false);
        assertThatThrownBy(() -> service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Mismatch",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.00"), null, null, null)),
                null), manager, managerAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Refund payments must equal refund total");

        verify(refundRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRefundPaymentThatExceedsOriginalPaymentBalance() throws Exception {
        Fixture fixture = fixture(new BigDecimal("1.0000"), new BigDecimal("2.0000"));
        when(returnRepository.findByIdForUpdate(fixture.returnRecord().getId())).thenReturn(Optional.of(fixture.returnRecord()));
        when(saleRepository.findByIdForUpdate(fixture.sale().getId())).thenReturn(Optional.of(fixture.sale()));
        when(refundPaymentRepository.refundedAmountForOriginalPayment(fixture.payment().getId())).thenReturn(new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.create(new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Too much to original tender",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.75"), fixture.payment().getId(), null, null)),
                null), manager, managerAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Refund payment amount cannot exceed original payment balance");

        verify(refundRepository, never()).saveAndFlush(any());
        verify(inventoryService, never()).recordStockChange(any(), any());
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void createIdempotentlyReplaysCompletedRefund() throws Exception {
        IdempotencyProperties idempotencyProperties = new IdempotencyProperties();
        RefundService idempotentService = new RefundService(
                refundRepository,
                refundPaymentRepository,
                returnRepository,
                saleRepository,
                userRepository,
                inventoryService,
                cashLedgerService,
                auditService,
                new IdempotencyService(new InMemoryStore(), idempotencyProperties, objectMapper, transactions),
                objectMapper,
                properties,
                transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Fixture fixture = fixture(new BigDecimal("1.0000"), new BigDecimal("2.0000"));
        when(returnRepository.findByIdForUpdate(fixture.returnRecord().getId())).thenReturn(Optional.of(fixture.returnRecord()));
        when(saleRepository.findByIdForUpdate(fixture.sale().getId())).thenReturn(Optional.of(fixture.sale()));
        when(refundRepository.refundedQuantityForSaleItem(fixture.saleItem().getId())).thenReturn(new BigDecimal("1.0000"));

        RefundCreateRequest request = new RefundCreateRequest(
                fixture.returnRecord().getId(),
                "Idempotent",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, new BigDecimal("5.75"), fixture.payment().getId(), null, null)),
                null);

        IdempotencyResult first = idempotentService.createIdempotently(request, "refund-key", managerAuth());
        IdempotencyResult replay = idempotentService.createIdempotently(request, "refund-key", managerAuth());

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.body()).isEqualTo(first.body());
        verify(refundRepository).saveAndFlush(any(Refund.class));
    }

    private Fixture fixture(BigDecimal returnQuantity, BigDecimal saleQuantity) throws Exception {
        UUID taxCategoryId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        Product product = new Product(new ProductValues(
                "SKU-1",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal("2.0000"),
                new BigDecimal("5.0000"),
                null,
                null,
                true,
                true,
                false,
                null,
                taxCategoryId,
                List.of(),
                List.of(),
                Set.of(ProductCapability.ALLOW_RETURN)));
        Sale sale = sale(product, saleQuantity);
        SaleItem saleItem = saleItem(sale, product, saleQuantity);
        invoke(saleItem, "setCalculatedAmounts", new Class<?>[]{BigDecimal.class, BigDecimal.class, BigDecimal.class},
                new BigDecimal("10.00"), new BigDecimal("1.50"), new BigDecimal("11.50"));
        invoke(saleItem, "snapshotForCompletion");
        invoke(sale, "addItem", new Class<?>[]{SaleItem.class}, saleItem);
        invoke(sale, "setTotals", new Class<?>[]{BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                new BigDecimal("10.00"), BigDecimal.ZERO.setScale(2), new BigDecimal("1.50"), new BigDecimal("11.50"));
        Payment payment = payment(sale, new BigDecimal("11.50"));
        invoke(sale, "addPayment", new Class<?>[]{Payment.class}, payment);
        invoke(sale, "complete", new Class<?>[]{User.class, Instant.class}, manager, NOW.minusSeconds(60));
        Return returnRecord = returnRecord(sale);
        ReturnItem returnItem = returnItem(returnRecord, saleItem, returnQuantity);
        invoke(returnRecord, "addItem", new Class<?>[]{ReturnItem.class}, returnItem);
        return new Fixture(sale, saleItem, payment, returnRecord, taxCategoryId);
    }

    private Sale sale(Product product, BigDecimal quantity) throws Exception {
        Constructor<Sale> constructor = Sale.class.getDeclaredConstructor(
                Store.class,
                Register.class,
                RegisterSession.class,
                User.class,
                UUID.class,
                LocalDate.class,
                String.class,
                String.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store(), register(), registerSession(), manager, null, LocalDate.parse("2026-07-28"), "POS", "USD", false);
    }

    private SaleItem saleItem(Sale sale, Product product, BigDecimal quantity) throws Exception {
        Constructor<SaleItem> constructor = SaleItem.class.getDeclaredConstructor(
                Sale.class,
                Product.class,
                BigDecimal.class,
                BigDecimal.class,
                BigDecimal.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                UUID.class,
                String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(sale, product, quantity, new BigDecimal("5.0000"), BigDecimal.ZERO.setScale(2), false, false, null, null, null, null);
    }

    private Payment payment(Sale sale, BigDecimal amount) throws Exception {
        Constructor<Payment> constructor = Payment.class.getDeclaredConstructor(
                Sale.class,
                PaymentMethod.class,
                BigDecimal.class,
                String.class,
                BigDecimal.class,
                BigDecimal.class,
                String.class,
                String.class,
                User.class,
                Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(sale, PaymentMethod.CASH, amount, "USD", amount, BigDecimal.ZERO.setScale(2), null, null, manager, NOW.minusSeconds(55));
    }

    private Return returnRecord(Sale sale) throws Exception {
        Constructor<Return> constructor = Return.class.getDeclaredConstructor(Sale.class, User.class, Instant.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(sale, manager, NOW.minusSeconds(30), "Customer changed mind");
    }

    private ReturnItem returnItem(Return returnRecord, SaleItem saleItem, BigDecimal quantity) throws Exception {
        Constructor<ReturnItem> constructor = ReturnItem.class.getDeclaredConstructor(Return.class, SaleItem.class, BigDecimal.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(returnRecord, saleItem, quantity, "Customer changed mind");
    }

    private Store store() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000901"));
        when(store.getTimezone()).thenReturn("UTC");
        return store;
    }

    private Register register() {
        Register register = mock(Register.class);
        Store store = store();
        when(register.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000902"));
        when(register.getStore()).thenReturn(store);
        return register;
    }

    private RegisterSession registerSession() {
        RegisterSession session = mock(RegisterSession.class);
        Store store = store();
        Register register = register();
        when(session.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000903"));
        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(session.getAssignedCashier()).thenReturn(manager);
        when(session.getStore()).thenReturn(store);
        when(session.getRegister()).thenReturn(register);
        return session;
    }

    private UsernamePasswordAuthenticationToken managerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@example.test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("REFUND_CREATE")));
    }

    private UsernamePasswordAuthenticationToken approvalAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@example.test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("REFUND_CREATE"), new SimpleGrantedAuthority("REFUND_APPROVE")));
    }

    private UsernamePasswordAuthenticationToken noApprovalAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@example.test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("REFUND_CREATE")));
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private record Fixture(Sale sale, SaleItem saleItem, Payment payment, Return returnRecord, UUID taxCategoryId) {
    }

    private static final class ImmediateTransactions implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }

    private static final class InMemoryStore implements IdempotencyStore {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();

        @Override
        public synchronized void deleteExpired(UUID userId, String endpoint, String idempotencyKey, Instant now) {
            records.computeIfPresent(scope(userId, endpoint, idempotencyKey),
                    (ignored, record) -> record.getExpiresAt().isBefore(now) ? null : record);
        }

        @Override
        public synchronized Optional<IdempotencyRecord> findActiveForUpdate(UUID userId, String endpoint, String idempotencyKey, Instant now) {
            IdempotencyRecord record = records.get(scope(userId, endpoint, idempotencyKey));
            if (record == null || !record.getExpiresAt().isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        @Override
        public synchronized IdempotencyRecord save(IdempotencyRecord record) {
            records.put(scope(record.getUserId(), record.getEndpoint(), record.getIdempotencyKey()), record);
            return record;
        }

        @Override
        public synchronized long deleteExpiredBefore(Instant now) {
            long deleted = 0;
            Iterator<IdempotencyRecord> iterator = records.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getExpiresAt().isBefore(now)) {
                    iterator.remove();
                    deleted++;
                }
            }
            return deleted;
        }

        private static String scope(UUID userId, String endpoint, String idempotencyKey) {
            return userId + "|" + endpoint + "|" + idempotencyKey;
        }
    }
}
