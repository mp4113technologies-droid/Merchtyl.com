package com.merchtyl.cash;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterType;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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

class CashMovementServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-27T11:30:00Z");

    private final CashMovementRepository cashMovementRepository = mock(CashMovementRepository.class);
    private final RegisterSessionRepository registerSessionRepository = mock(RegisterSessionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CashLedgerService cashLedgerService = mock(CashLedgerService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CashMovementProperties properties = new CashMovementProperties();
    private final StoreAccessService storeAccessService = mock(StoreAccessService.class);
    private final Store store = mock(Store.class);
    private final Register register = mock(Register.class);
    private final RegisterSession session = mock(RegisterSession.class);
    private final User actor = mock(User.class);
    private final CashMovementService service = new CashMovementService(
            cashMovementRepository,
            registerSessionRepository,
            userRepository,
            cashLedgerService,
            auditService,
            properties,
            storeAccessService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCurrencyCode()).thenReturn("USD");
        when(store.getTimezone()).thenReturn("America/Los_Angeles");
        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getStore()).thenReturn(store);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStore()).thenReturn(store);
        when(session.getRegister()).thenReturn(register);
        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(session.getAssignedCashier()).thenReturn(actor);
        when(actor.getId()).thenReturn(USER_ID);
        when(actor.isEnabled()).thenReturn(true);
        when(actor.isLocked()).thenReturn(false);
        when(registerSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmailIgnoreCase("cashier@example.test")).thenReturn(Optional.of(actor));
        when(cashMovementRepository.saveAndFlush(any(CashMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createCashOutRecordsMovementLedgerAndAuditAtomically() {
        CashMovementResponse response = service.create(request(CashMovementType.CASH_OUT, null), cashierAuth());

        assertThat(response.registerSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.type()).isEqualTo(CashMovementType.CASH_OUT);
        assertThat(response.direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.reason()).isEqualTo("Drawer paid petty cash");
        assertThat(response.createdBy()).isEqualTo(USER_ID);
        assertThat(response.approvedBy()).isNull();

        ArgumentCaptor<CashLedgerEntryCommand> ledgerCommand = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedgerService).append(ledgerCommand.capture());
        assertThat(ledgerCommand.getValue().sourceType()).isEqualTo(CashLedgerSourceType.CASH_MOVEMENT);
        assertThat(ledgerCommand.getValue().sourceId()).isEqualTo(response.id());
        assertThat(ledgerCommand.getValue().operationId()).isEqualTo(response.id());
        assertThat(ledgerCommand.getValue().direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledgerCommand.getValue().businessDate()).isEqualTo(LocalDate.parse("2026-07-27"));
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void payoutCreatesDistinctCashOutAndReducesExpectedCashWithoutCreatingOtherDomainRecords() {
        when(register.getType()).thenReturn(RegisterType.RETAIL);
        when(cashLedgerService.expectedCash(session)).thenReturn(new BigDecimal("700.00"));

        CashMovementResponse response = service.create(payoutRequest("50.00", "VENDOR_PAYMENT", "Milk supplier"), cashierAuth());

        assertThat(response.type()).isEqualTo(CashMovementType.PAYOUT);
        assertThat(response.direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.reason()).isEqualTo("VENDOR_PAYMENT");
        assertThat(response.notes()).isEqualTo("Milk supplier");

        ArgumentCaptor<CashLedgerEntryCommand> ledgerCommand = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedgerService).append(ledgerCommand.capture());
        assertThat(ledgerCommand.getValue().sourceType()).isEqualTo(CashLedgerSourceType.CASH_MOVEMENT);
        assertThat(ledgerCommand.getValue().direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledgerCommand.getValue().amount()).isEqualByComparingTo("50.00");
        verify(cashLedgerService).expectedCash(session);
    }

    @Test
    void payoutRejectsAmountThatExceedsAuthoritativeExpectedCash() {
        when(register.getType()).thenReturn(RegisterType.RETAIL);
        when(cashLedgerService.expectedCash(session)).thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.create(payoutRequest("150.00", "VENDOR_PAYMENT", null), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cash payout exceeds expected till cash")
                .hasMessageContaining("100.00")
                .hasMessageContaining("150.00");

        verify(cashMovementRepository, never()).saveAndFlush(any());
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void payoutRequiresRetailRegisterAndStructuredReason() {
        when(register.getType()).thenReturn(RegisterType.FOOD_SERVICE);

        assertThatThrownBy(() -> service.create(payoutRequest("25.00", "VENDOR_PAYMENT", null), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("RETAIL_REGISTER_REQUIRED");

        when(register.getType()).thenReturn(RegisterType.RETAIL);
        assertThatThrownBy(() -> service.create(payoutRequest("25.00", "UNSTRUCTURED", null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported payout reason");
    }

    @Test
    void otherPayoutRequiresNote() {
        when(register.getType()).thenReturn(RegisterType.RETAIL);

        assertThatThrownBy(() -> service.create(payoutRequest("25.00", "OTHER", " "), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A note is required for Other payouts");
    }

    @Test
    void payoutRejectsNonPositiveAndExcessPrecisionAmounts() {
        when(register.getType()).thenReturn(RegisterType.RETAIL);

        assertThatThrownBy(() -> service.create(payoutRequest("0.00", "PETTY_CASH", null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("amount must be greater than 0.00");
        assertThatThrownBy(() -> service.create(payoutRequest("-1.00", "PETTY_CASH", null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("amount must be greater than 0.00");
        assertThatThrownBy(() -> service.create(payoutRequest("1.001", "PETTY_CASH", null), cashierAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("amount may include no more than 2 decimal places");
    }

    @Test
    void payoutRejectsClosedSessionAndCrossTenantActor() {
        when(session.getStatus()).thenReturn(RegisterSessionStatus.CLOSED);
        assertThatThrownBy(() -> service.create(payoutRequest("25.00", "STORE_EXPENSE", null), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Register session is not open");

        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(store.getTenantId()).thenReturn(UUID.randomUUID());
        when(actor.getTenantId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.create(payoutRequest("25.00", "STORE_EXPENSE", null), cashierAuth()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Register session is outside the authenticated tenant");
    }

    @Test
    void managerReversalPreservesOriginalAndPostsEqualCashInExactlyOnce() {
        when(session.isBusinessDayOperational()).thenReturn(true);
        CashMovement original = new CashMovement(
                store, register, session, CashMovementType.PAYOUT, CashLedgerDirection.OUT,
                new BigDecimal("50.00"), "USD", "VENDOR_PAYMENT", "Milk supplier", actor,
                OCCURRED_AT, null, null, null);
        when(cashMovementRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(cashMovementRepository.existsByReversedMovement_Id(original.getId())).thenReturn(false);

        CashMovementResponse response = service.reversePayout(
                original.getId(), new CashMovementReversalRequest("Entered twice"), managerApproverAuth());

        assertThat(response.type()).isEqualTo(CashMovementType.PAYOUT_REVERSAL);
        assertThat(response.direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.reversedMovementId()).isEqualTo(original.getId());
        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedgerService).append(ledger.capture());
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void payoutCannotBeReversedTwice() {
        when(session.isBusinessDayOperational()).thenReturn(true);
        CashMovement original = new CashMovement(
                store, register, session, CashMovementType.PAYOUT, CashLedgerDirection.OUT,
                new BigDecimal("50.00"), "USD", "VENDOR_PAYMENT", null, actor,
                OCCURRED_AT, null, null, null);
        when(cashMovementRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(cashMovementRepository.existsByReversedMovement_Id(original.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.reversePayout(
                original.getId(), new CashMovementReversalRequest("Duplicate"), managerApproverAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cash payout has already been reversed");

        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void correctionRequiresExplicitDirection() {
        assertThatThrownBy(() -> service.create(request(CashMovementType.CORRECTION, null), managerApproverAuth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("direction is required");

        verify(cashMovementRepository, never()).saveAndFlush(any());
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void configuredApprovalRequiresApprovalPermission() {
        properties.setApprovalRequiredTypes(Set.of(CashMovementType.SAFE_DROP));

        assertThatThrownBy(() -> service.create(request(CashMovementType.SAFE_DROP, null), cashierAuth()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("SAFE_DROP requires cash movement approval");

        verify(cashMovementRepository, never()).saveAndFlush(any());
        verify(cashLedgerService, never()).append(any());
    }

    @Test
    void configuredApprovalCapturesApproverMetadata() {
        properties.setApprovalRequiredTypes(Set.of(CashMovementType.SAFE_DROP));

        CashMovementResponse response = service.create(request(CashMovementType.SAFE_DROP, null), managerApproverAuth());

        assertThat(response.approvedBy()).isEqualTo(USER_ID);
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.approvalNotes()).isEqualTo("Manager approved");
    }

    @Test
    void ledgerFailurePreventsAudit() {
        when(cashLedgerService.append(any())).thenThrow(new ConflictException("Cash ledger operation already exists"));

        assertThatThrownBy(() -> service.create(request(CashMovementType.FLOAT_ADD, null), cashierAuth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cash ledger operation already exists");

        verify(cashMovementRepository).saveAndFlush(any(CashMovement.class));
        verify(auditService, never()).record(any());
    }

    private static CashMovementRequest request(CashMovementType type, CashLedgerDirection direction) {
        return new CashMovementRequest(
                SESSION_ID,
                type,
                direction,
                new BigDecimal("25.00"),
                " Drawer paid petty cash ",
                " Receipt in drawer ",
                OCCURRED_AT,
                " Manager approved ");
    }

    private static CashMovementRequest payoutRequest(String amount, String reason, String notes) {
        return new CashMovementRequest(
                SESSION_ID,
                CashMovementType.PAYOUT,
                null,
                new BigDecimal(amount),
                reason,
                notes,
                OCCURRED_AT,
                null);
    }

    private static TestingAuthenticationToken cashierAuth() {
        return new TestingAuthenticationToken(
                "cashier@example.test",
                null,
                List.of(new SimpleGrantedAuthority("CASH_MOVEMENT_CREATE")));
    }

    private static TestingAuthenticationToken managerApproverAuth() {
        return new TestingAuthenticationToken(
                "cashier@example.test",
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_MANAGER"),
                        new SimpleGrantedAuthority("CASH_MOVEMENT_CREATE"),
                        new SimpleGrantedAuthority("CASH_MOVEMENT_APPROVE")));
    }
}
