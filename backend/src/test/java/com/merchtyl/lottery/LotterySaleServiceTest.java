package com.merchtyl.lottery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerEntryCommand;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashLedgerSourceType;
import com.merchtyl.common.ConflictException;
import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyState;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.tax.TaxJurisdiction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotterySaleServiceTest {
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000804");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000805");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000806");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    private final LotterySaleRepository lotterySaleRepository = mock(LotterySaleRepository.class);
    private final LotterySaleCancellationRepository lotterySaleCancellationRepository = mock(LotterySaleCancellationRepository.class);
    private final LotteryOperatorRepository lotteryOperatorRepository = mock(LotteryOperatorRepository.class);
    private final LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository = mock(LotteryPayoutPolicyRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final RegisterRepository registerRepository = mock(RegisterRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final RegisterSessionRepository registerSessionRepository = mock(RegisterSessionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FeatureService featureService = mock(FeatureService.class);
    private final CashLedgerService cashLedgerService = mock(CashLedgerService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TransactionOperations transactions = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };
    private final LotteryOperator operator = mock(LotteryOperator.class);
    private final LotteryPayoutPolicy policy = mock(LotteryPayoutPolicy.class);
    private final TaxJurisdiction jurisdiction = mock(TaxJurisdiction.class);
    private final Store store = mock(Store.class);
    private final Register register = mock(Register.class);
    private final Device device = mock(Device.class);
    private final RegisterSession registerSession = mock(RegisterSession.class);
    private final User cashier = new User("cashier@example.test", "Cashier One", "hash");
    private final LotterySaleService service = new LotterySaleService(
            lotterySaleRepository,
            lotterySaleCancellationRepository,
            lotteryOperatorRepository,
            lotteryPayoutPolicyRepository,
            storeRepository,
            registerRepository,
            deviceRepository,
            registerSessionRepository,
            userRepository,
            featureService,
            cashLedgerService,
            auditService,
            idempotencyService,
            objectMapper,
            transactions,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(jurisdiction.getId()).thenReturn(JURISDICTION_ID);
        when(operator.getId()).thenReturn(OPERATOR_ID);
        when(operator.getCode()).thenReturn("STATE");
        when(operator.getName()).thenReturn("State Lottery");
        when(operator.getJurisdiction()).thenReturn(jurisdiction);
        when(operator.isActive()).thenReturn(true);
        when(policy.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000807"));

        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        when(store.getCurrencyCode()).thenReturn("USD");
        when(store.getTimezone()).thenReturn("America/Los_Angeles");
        when(store.isActive()).thenReturn(true);

        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getStore()).thenReturn(store);
        when(register.getCode()).thenReturn("FRONT");
        when(register.getName()).thenReturn("Front Register");
        when(register.isActive()).thenReturn(true);

        when(device.getId()).thenReturn(DEVICE_ID);
        when(device.getStore()).thenReturn(store);
        when(device.getRegister()).thenReturn(register);
        when(device.getDeviceIdentifier()).thenReturn("browser:test");
        when(device.getDisplayName()).thenReturn("Front Browser");
        when(device.isActive()).thenReturn(true);

        when(registerSession.getId()).thenReturn(SESSION_ID);
        when(registerSession.getStore()).thenReturn(store);
        when(registerSession.getRegister()).thenReturn(register);
        when(registerSession.getDevice()).thenReturn(device);
        when(registerSession.getAssignedCashier()).thenReturn(cashier);
        when(registerSession.getStatus()).thenReturn(RegisterSessionStatus.OPEN);

        when(lotteryOperatorRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator));
        when(lotteryPayoutPolicyRepository.findEffectivePolicies(eq(OPERATOR_ID), eq(JURISDICTION_ID), eq(STORE_ID), any(), any(PageRequest.class)))
                .thenReturn(List.of(policy));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(registerRepository.findById(REGISTER_ID)).thenReturn(Optional.of(register));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(registerSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(registerSession));
        when(registerSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(registerSession));
        when(userRepository.findByEmailIgnoreCase("cashier@example.test")).thenReturn(Optional.of(cashier));
        when(lotterySaleRepository.saveAndFlush(any(LotterySale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lotterySaleCancellationRepository.saveAndFlush(any(LotterySaleCancellation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cashSaleRequiresOpenSessionAndWritesLedgerAndAudit() {
        LotterySaleResponse response = service.record(request(PaymentMethod.CASH, SESSION_ID), cashier, cashierAuth());

        assertThat(response.operatorId()).isEqualTo(OPERATOR_ID);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.registerSessionId()).isEqualTo(SESSION_ID);

        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedgerService).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.LOTTERY_SALE_CASH);
        assertThat(ledger.getValue().sourceId()).isEqualTo(response.id());
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("25.00");
        assertThat(ledger.getValue().operationId()).isEqualTo(response.operationId());
        assertThat(ledger.getValue().businessDate()).hasToString("2026-07-27");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOTTERY_SALE_RECORDED);
        assertThat(audit.getValue().entityType()).isEqualTo("LOTTERY_SALE");
        assertThat(audit.getValue().storeId()).isEqualTo(STORE_ID);
        verify(featureService).requireEnabled(FeatureCode.LOTTERY_SALES, STORE_ID, REGISTER_ID);
    }

    @Test
    void nonCashSaleDoesNotAffectPhysicalCashAndDoesNotRequireSession() {
        LotterySaleResponse response = service.record(request(PaymentMethod.CREDIT, null), cashier, cashierAuth());

        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CREDIT);
        assertThat(response.registerSessionId()).isNull();
        verify(cashLedgerService, never()).append(any());
        verify(auditService).record(any());
    }

    @Test
    void cashSaleRejectsClosedRegisterSession() {
        when(registerSession.getStatus()).thenReturn(RegisterSessionStatus.CLOSED);

        assertThatThrownBy(() -> service.record(request(PaymentMethod.CASH, SESSION_ID), cashier, cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Register session is not open");
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void recordIdempotentlyDelegatesToIdempotencyServiceWithRequestBody() {
        IdempotencyResult expected = new IdempotencyResult(
                IdempotencyState.COMPLETED,
                201,
                "application/json",
                "{\"id\":\"lottery-sale\"}",
                false);
        when(idempotencyService.execute(any(), any(), any(), any(), any())).thenReturn(expected);

        IdempotencyResult result = service.recordIdempotently(request(PaymentMethod.CASH, SESSION_ID), "lottery-key", cashierAuth());

        assertThat(result).isEqualTo(expected);
        verify(idempotencyService).execute(
                org.mockito.Mockito.eq(cashier.getId()),
                org.mockito.Mockito.eq("POST /api/v1/lottery/sales"),
                org.mockito.Mockito.eq("lottery-key"),
                org.mockito.Mockito.contains(OPERATOR_ID.toString()),
                any());
    }

    @Test
    void cancelCashSalePreservesOriginalAndWritesCashRefundLedgerAndAudit() {
        LotterySale sale = sale(PaymentMethod.CASH, registerSession);
        when(lotterySaleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);

        LotterySaleCancellationResponse response = service.cancel(
                sale.getId(),
                new LotteryAdjustmentRequest("Customer changed mind"),
                cashier,
                cashierAuth());

        assertThat(response.originalSaleId()).isEqualTo(sale.getId());
        assertThat(response.cashReturned()).isTrue();
        assertThat(response.reason()).isEqualTo("Customer changed mind");
        assertThat(sale.getStatus()).isEqualTo(LotterySaleStatus.CANCELLED);
        verify(cashLedgerService).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.LOTTERY_SALE_CANCELLATION_CASH);
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledger.getValue().sourceId()).isEqualTo(response.id());
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("25.00");
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOTTERY_SALE_CANCELLED);
    }

    @Test
    void cancelNonCashSaleDoesNotWriteCashLedger() {
        LotterySale sale = sale(PaymentMethod.CREDIT, null);
        when(lotterySaleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));

        LotterySaleCancellationResponse response = service.cancel(
                sale.getId(),
                new LotteryAdjustmentRequest("Terminal voided"),
                cashier,
                cashierAuth());

        assertThat(response.cashReturned()).isFalse();
        assertThat(sale.getStatus()).isEqualTo(LotterySaleStatus.CANCELLED);
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void cancelIdempotentlyDelegatesToIdempotencyService() {
        IdempotencyResult expected = new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                "application/json",
                "{\"id\":\"cancelled\"}",
                false);
        when(idempotencyService.execute(any(), any(), any(), any(), any())).thenReturn(expected);

        IdempotencyResult result = service.cancelIdempotently(
                UUID.fromString("00000000-0000-0000-0000-000000000999"),
                new LotteryAdjustmentRequest("Duplicate ticket"),
                "cancel-key",
                cashierAuth());

        assertThat(result).isSameAs(expected);
        verify(idempotencyService).execute(
                org.mockito.Mockito.eq(cashier.getId()),
                org.mockito.Mockito.eq("POST /api/v1/lottery/sales/{id}/cancel"),
                org.mockito.Mockito.eq("cancel-key"),
                org.mockito.Mockito.contains("Duplicate ticket"),
                any());
    }

    private LotterySale sale(PaymentMethod paymentMethod, RegisterSession session) {
        return new LotterySale(
                operator,
                "TERM-14",
                "TICKET-99",
                LotteryGameType.DRAW_TICKET,
                new BigDecimal("25.00"),
                "USD",
                paymentMethod,
                store,
                register,
                device,
                cashier,
                session,
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                NOW);
    }

    private static LotterySaleRequest request(PaymentMethod paymentMethod, UUID sessionId) {
        return new LotterySaleRequest(
                OPERATOR_ID,
                "TERM-14",
                "TICKET-99",
                LotteryGameType.DRAW_TICKET,
                new BigDecimal("25.00"),
                paymentMethod,
                STORE_ID,
                REGISTER_ID,
                DEVICE_ID,
                sessionId,
                NOW);
    }

    private static UsernamePasswordAuthenticationToken cashierAuth() {
        return new UsernamePasswordAuthenticationToken(
                "cashier@example.test",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }
}
