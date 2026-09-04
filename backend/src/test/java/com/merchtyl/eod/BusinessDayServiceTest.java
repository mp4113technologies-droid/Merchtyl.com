package com.merchtyl.eod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditService;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashMovementRepository;
import com.merchtyl.common.ConflictException;
import com.merchtyl.features.FeatureService;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.inventory.InventoryTransactionRepository;
import com.merchtyl.lottery.LotteryPayoutRepository;
import com.merchtyl.lottery.LotteryPayoutReversalRepository;
import com.merchtyl.lottery.LotterySaleCancellationRepository;
import com.merchtyl.lottery.LotterySaleRepository;
import com.merchtyl.lottery.LotterySettlementRepository;
import com.merchtyl.refunds.RefundRepository;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDayServiceTest {
    @Mock private BusinessDayRepository businessDays;
    @Mock private EndOfDayReportRepository reports;
    @Mock private BusinessDayConfigurationRepository configurations;
    @Mock private StoreRepository stores;
    @Mock private UserRepository users;
    @Mock private RegisterSessionRepository registerSessions;
    @Mock private SaleRepository sales;
    @Mock private RefundRepository refunds;
    @Mock private CashMovementRepository cashMovements;
    @Mock private InventoryTransactionRepository inventoryTransactions;
    @Mock private InventoryBalanceRepository inventoryBalances;
    @Mock private LotterySaleRepository lotterySales;
    @Mock private LotteryPayoutRepository lotteryPayouts;
    @Mock private LotterySaleCancellationRepository lotterySaleCancellations;
    @Mock private LotteryPayoutReversalRepository lotteryPayoutReversals;
    @Mock private LotterySettlementRepository lotterySettlements;
    @Mock private CashLedgerService cashLedger;
    @Mock private FeatureService features;
    @Mock private AuditService audit;
    @Mock private StoreAccessService storeAccess;

    private BusinessDayService service;
    private Authentication authentication;
    private BusinessDay day;
    private Store store;
    private User actor;
    private UUID storeId;
    private UUID dayId;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        service = new BusinessDayService(
                businessDays, reports, configurations, stores, users, registerSessions, sales, refunds,
                cashMovements, inventoryTransactions, inventoryBalances, lotterySales, lotteryPayouts,
                lotterySaleCancellations, lotteryPayoutReversals, lotterySettlements, cashLedger, features,
                audit, new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-31T22:10:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "storeAccessService", storeAccess);
        authentication = mock(Authentication.class);
        day = mock(BusinessDay.class);
        store = mock(Store.class);
        actor = mock(User.class);
        storeId = UUID.randomUUID();
        dayId = UUID.randomUUID();
        businessDate = LocalDate.of(2026, 8, 31);

        lenient().when(authentication.getName()).thenReturn("owner@example.local");
        lenient().when(users.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(actor));
        lenient().when(businessDays.findByIdForUpdate(dayId)).thenReturn(Optional.of(day));
        lenient().when(day.getId()).thenReturn(dayId);
        lenient().when(day.getStore()).thenReturn(store);
        lenient().when(day.getBusinessDate()).thenReturn(businessDate);
        lenient().when(day.getStatus()).thenReturn(BusinessDayStatus.CLOSED);
        lenient().when(day.getVersion()).thenReturn(3L);
        lenient().when(store.getId()).thenReturn(storeId);
        lenient().when(store.getTimezone()).thenReturn("UTC");
        lenient().when(reports.existsByBusinessDay_Id(dayId)).thenReturn(true);
    }

    @Test
    void assignedStoreOperatorCanOpenTodaysBusinessDayWithoutManagementScope() {
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(configurations.findByStore_Id(storeId)).thenReturn(Optional.empty());
        when(businessDays.findByStore_IdAndStatusIn(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(List.of());
        when(businessDays.existsByStore_IdAndBusinessDate(storeId, businessDate)).thenReturn(false);
        when(businessDays.saveAndFlush(any(BusinessDay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getDisplayName()).thenReturn("Cashier One");

        BusinessDayResponse response = service.open(
                new BusinessDayOpenRequest(storeId, businessDate, false, null), authentication);

        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.status()).isEqualTo(BusinessDayStatus.OPEN);
        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess, never()).requireStoreManagement(authentication, storeId);
    }

    @Test
    void previousDayOverrideStillRequiresStoreManagement() {
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(configurations.findByStore_Id(storeId)).thenReturn(Optional.empty());
        when(businessDays.findByStore_IdAndStatusIn(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(List.of(day));

        assertThatThrownBy(() -> service.open(
                new BusinessDayOpenRequest(storeId, businessDate, true, "Approved exception"), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Previous business day");

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess).requireStoreManagement(authentication, storeId);
    }

    @Test
    void assignedStoreOperatorCanStartClosingWithoutManagementScope() {
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        stubResponseFields();

        service.startClosing(dayId, authentication);

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess, never()).requireStoreManagement(authentication, storeId);
        verify(day).startClosing(eq(actor), any(Instant.class));
    }

    @Test
    void assignedStoreOperatorCloseIsBlockedByOpenRegisterSession() {
        RegisterSession session = mock(RegisterSession.class);
        Register register = mock(Register.class);
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(session.getRegister()).thenReturn(register);
        when(register.getCode()).thenReturn("FRONT");
        when(registerSessions.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(session));

        assertThatThrownBy(() -> service.close(
                dayId,
                new BusinessDayCloseRequest(3L, null, null, true),
                authentication))
                .isInstanceOf(ClosingValidationException.class)
                .hasMessage("All registers must be closed before closing the business day.");

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess, never()).requireStoreManagement(authentication, storeId);
    }

    @Test
    void laterBusinessDayForSameStorePreventsReopen() {
        when(businessDays.existsByStore_IdAndBusinessDateGreaterThan(storeId, businessDate)).thenReturn(true);

        assertThatThrownBy(() -> service.reopen(dayId, new BusinessDayReopenRequest(3L, "Late sales"), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessage("LATER_BUSINESS_DAY_EXISTS");

        verify(businessDays).existsByStore_IdAndBusinessDateGreaterThan(storeId, businessDate);
        verify(audit).record(any());
    }

    @Test
    void laterBusinessDayForAnotherStoreDoesNotBlockReopen() {
        AtomicReference<BusinessDayStatus> status = new AtomicReference<>(BusinessDayStatus.CLOSED);
        when(day.getStatus()).thenAnswer(ignored -> status.get());
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(day.getOpenedAt()).thenReturn(Instant.parse("2026-08-31T08:00:00Z"));
        when(day.getOpenedBy()).thenReturn(actor);
        when(day.getReopenedBy()).thenReturn(actor);
        when(day.getReopenedAt()).thenReturn(Instant.parse("2026-08-31T22:10:00Z"));
        when(day.getReopenReason()).thenReturn("Late sales");
        when(store.getCode()).thenReturn("DOWNTOWN");
        when(store.getName()).thenReturn("Downtown");
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getDisplayName()).thenReturn("Owner One");
        when(businessDays.existsByStore_IdAndBusinessDateGreaterThan(storeId, businessDate)).thenReturn(false);
        when(businessDays.findByStore_IdAndStatusIn(any(), any())).thenReturn(List.of());
        when(businessDays.saveAndFlush(day)).thenReturn(day);
        doAnswer(invocation -> {
            status.set(BusinessDayStatus.REOPENED);
            return null;
        }).when(day).reopen(actor, Instant.parse("2026-08-31T22:10:00Z"), "Late sales");

        BusinessDayResponse response = service.reopen(dayId, new BusinessDayReopenRequest(3L, "Late sales"), authentication);

        assertThat(response.id()).isEqualTo(dayId);
        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.status()).isEqualTo(BusinessDayStatus.REOPENED);
        verify(day).reopen(actor, Instant.parse("2026-08-31T22:10:00Z"), "Late sales");
        verify(businessDays).saveAndFlush(day);
    }

    @Test
    void closedPreviousDayProducesOpenActionForNewStoreLocalDate() {
        useClock(Clock.fixed(Instant.parse("2026-09-01T03:10:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 1))).thenReturn(Optional.empty());
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdAndStatusInOrderByBusinessDateDescOpenedAtDesc(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(Optional.empty());
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.currentBusinessDay()).isNull();
        assertThat(response.previousBusinessDay().id()).isEqualTo(dayId);
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.HISTORICAL_CLOSED);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.OPEN);
    }

    @Test
    void openPreviousDayBlocksOpeningNewStoreLocalDateAndReturnsConcreteDay() {
        useClock(Clock.fixed(Instant.parse("2026-09-02T03:10:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(day.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 2))).thenReturn(Optional.empty());
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdAndStatusInOrderByBusinessDateDescOpenedAtDesc(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(Optional.of(day));
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(response.currentBusinessDay()).isNull();
        assertThat(response.previousBusinessDay().id()).isEqualTo(dayId);
        assertThat(response.previousBusinessDay().businessDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.PREVIOUS_DAY_STILL_OPEN);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.NONE);
    }

    @Test
    void closedCurrentDayProducesReopenAction() {
        useClock(Clock.fixed(Instant.parse("2026-09-01T03:10:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(day.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 1))).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDay().id()).isEqualTo(dayId);
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.CLOSED_TODAY);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.REOPEN);
    }

    @Test
    void historicalClosedDayCannotBeReopened() {
        useClock(Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.reopen(dayId, new BusinessDayReopenRequest(3L, "Late sales"), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("HISTORICAL_BUSINESS_DAY");
    }

    private void stubResponseFields() {
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(day.getOpenedAt()).thenReturn(Instant.parse("2026-08-31T12:00:00Z"));
        when(day.getOpenedBy()).thenReturn(actor);
        when(store.getCode()).thenReturn("DOWNTOWN");
        when(store.getName()).thenReturn("Downtown");
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getDisplayName()).thenReturn("Owner One");
    }

    private void useClock(Clock clock) {
        service = new BusinessDayService(
                businessDays, reports, configurations, stores, users, registerSessions, sales, refunds,
                cashMovements, inventoryTransactions, inventoryBalances, lotterySales, lotteryPayouts,
                lotterySaleCancellations, lotteryPayoutReversals, lotterySettlements, cashLedger, features,
                audit, new ObjectMapper(), clock);
    }
}
