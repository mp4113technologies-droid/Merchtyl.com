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
import com.merchtyl.common.ForbiddenOperationException;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class LotteryPayoutServiceTest {
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000706");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000707");
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-07-28");

    private final LotteryPayoutRepository lotteryPayoutRepository = mock(LotteryPayoutRepository.class);
    private final LotteryPayoutReversalRepository lotteryPayoutReversalRepository = mock(LotteryPayoutReversalRepository.class);
    private final LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository = mock(LotteryPayoutPolicyRepository.class);
    private final LotteryOperatorRepository lotteryOperatorRepository = mock(LotteryOperatorRepository.class);
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
    private final RegisterSession session = mock(RegisterSession.class);
    private final User cashier = new User("cashier@example.test", "Cashier One", "hash");
    private final LotteryPayoutService service = new LotteryPayoutService(
            lotteryPayoutRepository,
            lotteryPayoutReversalRepository,
            lotteryPayoutPolicyRepository,
            lotteryOperatorRepository,
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

        when(policy.getId()).thenReturn(POLICY_ID);
        when(policy.getOperator()).thenReturn(operator);
        when(policy.getStore()).thenReturn(store);
        when(policy.getCashierApprovalLimit()).thenReturn(new BigDecimal("100.00"));
        when(policy.getManagerApprovalThreshold()).thenReturn(new BigDecimal("250.00"));
        when(policy.getOperatorReferralThreshold()).thenReturn(new BigDecimal("500.00"));
        when(policy.getMaximumCashPayout()).thenReturn(new BigDecimal("400.00"));
        when(policy.getProtectedRegisterFloat()).thenReturn(new BigDecimal("50.00"));
        when(policy.isAllowCashPayout()).thenReturn(true);
        when(policy.isAllowStoreCredit()).thenReturn(true);
        when(policy.isRequireTicketValidation()).thenReturn(true);
        when(policy.isRequireAgeVerification()).thenReturn(true);
        when(policy.isRequireCustomerIdentification()).thenReturn(true);
        when(policy.isAllowAlternateRegister()).thenReturn(false);

        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        when(store.getCurrencyCode()).thenReturn("USD");
        when(store.getTimezone()).thenReturn("UTC");
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

        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStore()).thenReturn(store);
        when(session.getRegister()).thenReturn(register);
        when(session.getDevice()).thenReturn(device);
        when(session.getAssignedCashier()).thenReturn(cashier);
        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);

        when(lotteryOperatorRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator));
        when(lotteryPayoutPolicyRepository.findEffectivePolicies(eq(OPERATOR_ID), eq(JURISDICTION_ID), eq(STORE_ID), eq(BUSINESS_DATE), any(PageRequest.class)))
                .thenReturn(List.of(policy));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(registerRepository.findById(REGISTER_ID)).thenReturn(Optional.of(register));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(registerSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(registerSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmailIgnoreCase("cashier@example.test")).thenReturn(Optional.of(cashier));
        when(lotteryPayoutRepository.saveAndFlush(any(LotteryPayout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lotteryPayoutReversalRepository.saveAndFlush(any(LotteryPayoutReversal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashLedgerService.expectedCash(session)).thenReturn(new BigDecimal("300.00"));
        when(lotteryPayoutRepository.sumReservedCashObligations(eq(SESSION_ID), isNull())).thenReturn(BigDecimal.ZERO.setScale(2));
        when(lotteryPayoutRepository.sumReservedCashObligations(eq(SESSION_ID), any(UUID.class))).thenReturn(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void createResolvesPolicyAndSnapshotsVerificationRequirements() {
        LotteryPayoutResponse response = service.create(createRequest(new BigDecimal("75.00"), LotteryPayoutMethod.CASH), cashierAuth());

        assertThat(response.status()).isEqualTo(LotteryPayoutStatus.DRAFT);
        assertThat(response.policyId()).isEqualTo(POLICY_ID);
        assertThat(response.ticketValidationState()).isEqualTo(LotteryVerificationState.PENDING);
        assertThat(response.ageVerificationState()).isEqualTo(LotteryVerificationState.PENDING);
        assertThat(response.identificationVerificationState()).isEqualTo(LotteryVerificationState.PENDING);
        assertThat(response.cashierApprovalLimit()).isEqualByComparingTo("100.00");
        verify(featureService).requireEnabled(FeatureCode.LOTTERY_SALES, STORE_ID, REGISTER_ID);
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void validateRequiresConfiguredVerificationStates() {
        LotteryPayout payout = payout(new BigDecimal("75.00"), LotteryPayoutMethod.CASH);
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        LotteryPayoutResponse response = service.validate(payout.getId(), validationRequest(0), cashierAuth());

        assertThat(response.status()).isEqualTo(LotteryPayoutStatus.VALIDATED);
        assertThat(response.ticketValidationState()).isEqualTo(LotteryVerificationState.VERIFIED);
        assertThat(response.validatedBy()).isEqualTo(cashier.getId());
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOTTERY_PAYOUT_VALIDATED);
    }

    @Test
    void validateRefersPayoutsAtOperatorThreshold() {
        LotteryPayout payout = payout(new BigDecimal("500.00"), LotteryPayoutMethod.CASH);
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        LotteryPayoutResponse response = service.validate(payout.getId(), validationRequest(0), cashierAuth());

        assertThat(response.status()).isEqualTo(LotteryPayoutStatus.REFERRED_TO_OPERATOR);
        assertThat(response.approvals()).singleElement().satisfies(approval -> {
            assertThat(approval.approvalType()).isEqualTo(LotteryPayoutApprovalType.OPERATOR_REFERRAL);
            assertThat(approval.thresholdAmount()).isEqualByComparingTo("500.00");
        });
    }

    @Test
    void cashierCannotAuthorizeAboveCashierLimit() {
        LotteryPayout payout = validatedPayout(new BigDecimal("150.00"));
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        assertThatThrownBy(() -> service.authorize(payout.getId(), new LotteryPayoutAuthorizationRequest(0L, null), cashierAuth()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("manager approval");
        verify(lotteryPayoutRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.argThat(saved -> saved.getStatus() == LotteryPayoutStatus.AUTHORIZED));
    }

    @Test
    void managerAuthorizationCreatesApprovalRecord() {
        LotteryPayout payout = validatedPayout(new BigDecimal("150.00"));
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        LotteryPayoutResponse response = service.authorize(
                payout.getId(),
                new LotteryPayoutAuthorizationRequest(0L, "Manager approved"),
                managerAuth());

        assertThat(response.status()).isEqualTo(LotteryPayoutStatus.AUTHORIZED);
        assertThat(response.approvals()).singleElement().satisfies(approval -> {
            assertThat(approval.approvalType()).isEqualTo(LotteryPayoutApprovalType.MANAGER_APPROVAL);
            assertThat(approval.payoutAmount()).isEqualByComparingTo("150.00");
        });
    }

    @Test
    void cashPayoutRequiresOpenRegisterSession() {
        when(session.getStatus()).thenReturn(RegisterSessionStatus.CLOSED);

        assertThatThrownBy(() -> service.create(createRequest(new BigDecimal("75.00"), LotteryPayoutMethod.CASH), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Register session is not open");
    }

    @Test
    void availableCashSubtractsProtectedFloatAndReservedObligations() {
        when(lotteryPayoutRepository.sumReservedCashObligations(SESSION_ID, null)).thenReturn(new BigDecimal("75.00"));

        LotteryPayoutCashAvailabilityResponse response = service.availableCash(SESSION_ID, OPERATOR_ID);

        assertThat(response.expectedDrawerCash()).isEqualByComparingTo("300.00");
        assertThat(response.protectedRegisterFloat()).isEqualByComparingTo("50.00");
        assertThat(response.reservedObligations()).isEqualByComparingTo("75.00");
        assertThat(response.availablePayoutCash()).isEqualByComparingTo("175.00");
        assertThat(response.policyId()).isEqualTo(POLICY_ID);
        verify(featureService).requireEnabled(FeatureCode.LOTTERY_SALES, STORE_ID, REGISTER_ID);
    }

    @Test
    void completeCashWritesLedgerMarksPaidAndAudits() {
        LotteryPayout payout = authorizedPayout(new BigDecimal("75.00"));
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);

        LotteryPayoutResponse response = service.completeCash(payout.getId(), cashier, cashierAuth());

        assertThat(response.status()).isEqualTo(LotteryPayoutStatus.PAID);
        assertThat(response.paidBy()).isEqualTo(cashier.getId());
        assertThat(response.paidAt()).isEqualTo(NOW);
        verify(registerSessionRepository).findByIdForUpdate(SESSION_ID);
        verify(cashLedgerService).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.LOTTERY_PAYOUT_CASH);
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("75.00");
        assertThat(ledger.getValue().operationId()).isEqualTo(payout.getId());
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOTTERY_PAYOUT_PAID);
    }

    @Test
    void completeCashBlocksInsufficientAvailableCashAndDoesNotWriteLedger() {
        LotteryPayout payout = authorizedPayout(new BigDecimal("75.00"));
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        when(cashLedgerService.expectedCash(session)).thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.completeCash(payout.getId(), cashier, cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Insufficient available cash");

        verify(cashLedgerService, never()).append(any());
        assertThat(payout.getStatus()).isEqualTo(LotteryPayoutStatus.AUTHORIZED);
    }

    @Test
    void completeCashDoesNotSavePaidStateWhenLedgerAppendFails() {
        LotteryPayout payout = authorizedPayout(new BigDecimal("75.00"));
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        doThrow(new ConflictException("Cash ledger operation already exists"))
                .when(cashLedgerService)
                .append(any(CashLedgerEntryCommand.class));

        assertThatThrownBy(() -> service.completeCash(payout.getId(), cashier, cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cash ledger operation already exists");

        verify(lotteryPayoutRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.argThat(saved -> saved.getStatus() == LotteryPayoutStatus.PAID));
        verify(auditService, never()).record(org.mockito.ArgumentMatchers.argThat(record -> record.action() == AuditAction.LOTTERY_PAYOUT_PAID));
        assertThat(payout.getStatus()).isEqualTo(LotteryPayoutStatus.AUTHORIZED);
    }

    @Test
    void completeCashIdempotentlyDelegatesByActorEndpointAndKey() {
        IdempotencyResult expected = new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                "application/json",
                "{\"status\":\"PAID\"}",
                false);
        when(idempotencyService.execute(eq(cashier.getId()), eq("POST /api/v1/lottery/payouts/{id}/complete-cash"), eq("pay-key"), any(), any()))
                .thenReturn(expected);

        IdempotencyResult response = service.completeCashIdempotently(UUID.fromString("00000000-0000-0000-0000-000000000799"), "pay-key", cashierAuth());

        assertThat(response).isSameAs(expected);
    }

    @Test
    void reversePaidCashPayoutWritesCompensatingLedgerAndAudits() {
        LotteryPayout payout = authorizedPayout(new BigDecimal("75.00"));
        payout.completeCash(cashier, NOW);
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);

        LotteryPayoutReversalResponse response = service.reverse(
                payout.getId(),
                new LotteryAdjustmentRequest("Ticket validation reversed"),
                cashier,
                managerAuth());

        assertThat(response.originalPayoutId()).isEqualTo(payout.getId());
        assertThat(response.reason()).isEqualTo("Ticket validation reversed");
        assertThat(payout.getStatus()).isEqualTo(LotteryPayoutStatus.REVERSED);
        verify(cashLedgerService).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.LOTTERY_PAYOUT_REVERSAL);
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(ledger.getValue().sourceId()).isEqualTo(response.id());
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("75.00");
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOTTERY_PAYOUT_REVERSED);
    }

    @Test
    void reverseBlocksUnpaidPayouts() {
        LotteryPayout payout = authorizedPayout(new BigDecimal("75.00"));
        when(lotteryPayoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        assertThatThrownBy(() -> service.reverse(
                payout.getId(),
                new LotteryAdjustmentRequest("Not paid"),
                cashier,
                managerAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only paid lottery payouts");

        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void reverseIdempotentlyDelegatesByActorEndpointAndKey() {
        IdempotencyResult expected = new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                "application/json",
                "{\"id\":\"reversed\"}",
                false);
        when(idempotencyService.execute(eq(cashier.getId()), eq("POST /api/v1/lottery/payouts/{id}/reverse"), eq("reverse-key"), any(), any()))
                .thenReturn(expected);

        IdempotencyResult response = service.reverseIdempotently(
                UUID.fromString("00000000-0000-0000-0000-000000000799"),
                new LotteryAdjustmentRequest("Manager correction"),
                "reverse-key",
                cashierAuth());

        assertThat(response).isSameAs(expected);
    }

    private LotteryPayout validatedPayout(BigDecimal amount) {
        LotteryPayout payout = payout(amount, LotteryPayoutMethod.CASH);
        payout.validate(
                LotteryVerificationState.VERIFIED,
                LotteryVerificationState.VERIFIED,
                LotteryVerificationState.VERIFIED,
                "VALID-1",
                cashier,
                NOW,
                LotteryPayoutStatus.VALIDATED);
        return payout;
    }

    private LotteryPayout authorizedPayout(BigDecimal amount) {
        LotteryPayout payout = validatedPayout(amount);
        payout.authorize(cashier, NOW, LotteryPayoutApprovalType.CASHIER_LIMIT, new BigDecimal("100.00"), null);
        return payout;
    }

    private LotteryPayout payout(BigDecimal amount, LotteryPayoutMethod method) {
        return new LotteryPayout(
                operator,
                policy,
                store,
                register,
                device,
                cashier,
                session,
                "TICKET-1",
                amount,
                "USD",
                method,
                BUSINESS_DATE,
                NOW,
                null);
    }

    private static LotteryPayoutCreateRequest createRequest(BigDecimal amount, LotteryPayoutMethod method) {
        return new LotteryPayoutCreateRequest(
                OPERATOR_ID,
                STORE_ID,
                REGISTER_ID,
                DEVICE_ID,
                SESSION_ID,
                "TICKET-1",
                amount,
                method,
                BUSINESS_DATE,
                NOW,
                null);
    }

    private static LotteryPayoutValidationRequest validationRequest(long version) {
        return new LotteryPayoutValidationRequest(
                version,
                LotteryVerificationState.VERIFIED,
                LotteryVerificationState.VERIFIED,
                LotteryVerificationState.VERIFIED,
                "VALID-1");
    }

    private static UsernamePasswordAuthenticationToken cashierAuth() {
        return new UsernamePasswordAuthenticationToken(
                "cashier@example.test",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER"), new SimpleGrantedAuthority("LOTTERY_PAYOUT_RECORD")));
    }

    private static UsernamePasswordAuthenticationToken managerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "cashier@example.test",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("LOTTERY_PAYOUT_APPROVE")));
    }
}
