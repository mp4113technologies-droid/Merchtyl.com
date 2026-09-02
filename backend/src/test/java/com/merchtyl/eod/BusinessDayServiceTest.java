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
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
        when(day.getId()).thenReturn(dayId);
        when(day.getStore()).thenReturn(store);
        when(day.getBusinessDate()).thenReturn(businessDate);
        when(day.getStatus()).thenReturn(BusinessDayStatus.CLOSED);
        when(day.getVersion()).thenReturn(3L);
        when(store.getId()).thenReturn(storeId);
        lenient().when(reports.existsByBusinessDay_Id(dayId)).thenReturn(true);
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
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.NOT_OPENED);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.OPEN);
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
