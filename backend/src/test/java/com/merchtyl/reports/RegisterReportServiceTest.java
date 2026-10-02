package com.merchtyl.reports;

import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.eod.BusinessDay;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.registersession.RegisterBusinessDayCashState;
import com.merchtyl.registersession.RegisterBusinessDayCashStateRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000602");
    private static final UUID CASHIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000603");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000604");

    private final RegisterSessionRepository registerSessionRepository = mock(RegisterSessionRepository.class);
    private final CashLedgerService cashLedgerService = mock(CashLedgerService.class);
    private final StoreAccessService storeAccessService = mock(StoreAccessService.class);
    private final User actor = mock(User.class);
    private final RegisterReportService service = new RegisterReportService(
            registerSessionRepository,
            cashLedgerService,
            storeAccessService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void summarizesRegisterCashBucketsAndVariance() {
        when(actor.getTenantId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000699"));
        when(storeAccessService.currentTenantUser(any())).thenReturn(actor);
        RegisterSession session = session();
        when(registerSessionRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(session));
        when(registerSessionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session), PageRequest.of(0, 5), 1));
        when(cashLedgerService.breakdowns(List.of(session))).thenReturn(Map.of(SESSION_ID, breakdown()));

        RegisterReportResponse response = service.summarize(new RegisterReportRequest(
                STORE_ID,
                REGISTER_ID,
                CASHIER_ID,
                RegisterSessionStatus.CLOSED,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"), 0, 5), mock(org.springframework.security.core.Authentication.class));

        assertThat(response.openingCash()).isEqualByComparingTo("100.00");
        assertThat(response.retailCashReceived()).isEqualByComparingTo("250.00");
        assertThat(response.retailChange()).isEqualByComparingTo("30.00");
        assertThat(response.retailCash()).isEqualByComparingTo("220.00");
        assertThat(response.lotteryCashSales()).isEqualByComparingTo("80.00");
        assertThat(response.lotteryPayouts()).isEqualByComparingTo("25.00");
        assertThat(response.payoutReversals()).isEqualByComparingTo("5.00");
        assertThat(response.lotterySaleCancellations()).isEqualByComparingTo("10.00");
        assertThat(response.lotteryCash()).isEqualByComparingTo("50.00");
        assertThat(response.refunds()).isEqualByComparingTo("12.00");
        assertThat(response.cashMovementIn()).isEqualByComparingTo("40.00");
        assertThat(response.cashMovementOut()).isEqualByComparingTo("15.00");
        assertThat(response.cashMovements()).isEqualByComparingTo("25.00");
        assertThat(response.expectedCash()).isEqualByComparingTo("383.00");
        assertThat(response.countedCash()).isEqualByComparingTo("380.00");
        assertThat(response.variance()).isEqualByComparingTo("-3.00");
        assertThat(response.sessionCount()).isEqualTo(1);
        assertThat(response.closedSessionCount()).isEqualTo(1);
        assertThat(response.rows().content()).singleElement().satisfies(row -> {
            assertThat(row.registerSessionId()).isEqualTo(SESSION_ID);
            assertThat(row.currencyCode()).isEqualTo("USD");
            assertThat(row.cashierDisplayName()).isEqualTo("Ada Cashier");
            assertThat(row.retailCash()).isEqualByComparingTo("220.00");
            assertThat(row.lotteryCash()).isEqualByComparingTo("50.00");
            assertThat(row.cashMovements()).isEqualByComparingTo("25.00");
        });
        assertThat(response.generatedAt()).isEqualTo(NOW);
        verify(cashLedgerService).breakdowns(List.of(session));
        verify(cashLedgerService, never()).breakdown(any(RegisterSession.class));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(registerSessionRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("openedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void reconcilesOnlySessionsWithMatchingExpectedAndCountedPopulation() {
        when(actor.getTenantId()).thenReturn(UUID.randomUUID());
        when(storeAccessService.currentTenantUser(any())).thenReturn(actor);
        UUID day1Id = UUID.randomUUID();
        UUID day2Id = UUID.randomUUID();
        UUID day3Id = UUID.randomUUID();
        RegisterSession day1 = session(day1Id, RegisterSessionStatus.CLOSED,
                "1000.00", "1000.00", LocalDate.parse("2026-09-01"));
        RegisterSession day2 = session(day2Id, RegisterSessionStatus.CLOSED,
                "700.00", "700.00", LocalDate.parse("2026-09-02"));
        RegisterSession day3 = session(day3Id, RegisterSessionStatus.OPEN,
                null, null, LocalDate.parse("2026-09-03"));
        List<RegisterSession> sessions = List.of(day3, day2, day1);
        when(registerSessionRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(sessions);
        when(registerSessionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(sessions, PageRequest.of(0, 5), 3));
        when(cashLedgerService.breakdowns(sessions)).thenReturn(Map.of(
                day1Id, breakdown("300.00", "700.00", "0.00"),
                day2Id, breakdown("300.00", "500.00", "100.00"),
                day3Id, breakdown("300.00", "200.00", "0.00")));

        RegisterReportResponse response = service.summarize(new RegisterReportRequest(
                STORE_ID, null, null, null, LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-03"), 0, 5), mock(org.springframework.security.core.Authentication.class));

        assertThat(response.sessionCount()).isEqualTo(3);
        assertThat(response.closedSessionCount()).isEqualTo(2);
        assertThat(response.openingCash()).isEqualByComparingTo("900.00");
        assertThat(response.retailCashReceived()).isEqualByComparingTo("1400.00");
        assertThat(response.expectedCash()).isEqualByComparingTo("1700.00");
        assertThat(response.countedCash()).isEqualByComparingTo("1700.00");
        assertThat(response.variance()).isZero();
        assertThat(response.rows().content()).filteredOn(row -> row.status() == RegisterSessionStatus.OPEN)
                .singleElement().satisfies(row -> {
                    assertThat(row.expectedCash()).isEqualByComparingTo("500.00");
                    assertThat(row.countedCash()).isNull();
                    assertThat(row.variance()).isNull();
                });
    }

    @Test
    void twentyDailySessionsKeepIndependentOpeningFloats() {
        when(actor.getTenantId()).thenReturn(UUID.randomUUID());
        when(storeAccessService.currentTenantUser(any())).thenReturn(actor);
        List<RegisterSession> sessions = new ArrayList<>();
        Map<UUID, CashLedgerBreakdownResponse> breakdowns = new LinkedHashMap<>();
        for (int day = 1; day <= 20; day++) {
            UUID id = UUID.randomUUID();
            RegisterSession session = session(id, RegisterSessionStatus.CLOSED,
                    "300.00", "300.00", LocalDate.of(2026, 9, day));
            sessions.add(session);
            breakdowns.put(id, breakdown("300.00", "0.00", "0.00"));
        }
        when(registerSessionRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(sessions);
        when(registerSessionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(sessions.subList(0, 5), PageRequest.of(0, 5), 20));
        when(cashLedgerService.breakdowns(sessions)).thenReturn(breakdowns);

        RegisterReportResponse response = service.summarize(new RegisterReportRequest(
                STORE_ID, null, null, null, LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-20"), 0, 5), mock(org.springframework.security.core.Authentication.class));

        assertThat(response.rows().content()).hasSize(5).allSatisfy(row ->
                assertThat(row.openingCash()).isEqualByComparingTo("300.00"));
        assertThat(response.rows().totalElements()).isEqualTo(20);
        assertThat(response.rows().totalPages()).isEqualTo(4);
        assertThat(response.openingCash()).isEqualByComparingTo("6000.00");
        assertThat(response.expectedCash()).isEqualByComparingTo("6000.00");
    }

    @Test
    void secondPageReturnsNextTenWhileSummaryStillUsesAllFilteredSessions() {
        when(actor.getTenantId()).thenReturn(UUID.randomUUID());
        when(storeAccessService.currentTenantUser(any())).thenReturn(actor);
        List<RegisterSession> sessions = new ArrayList<>();
        Map<UUID, CashLedgerBreakdownResponse> breakdowns = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            UUID id = new UUID(0, index + 1);
            RegisterSession session = session(id, RegisterSessionStatus.CLOSED,
                    "100.00", "100.00", LocalDate.of(2026, 9, 20 - index));
            when(session.getOpenedAt()).thenReturn(Instant.parse("2026-09-20T12:00:00Z").minusSeconds(index));
            sessions.add(session);
            breakdowns.put(id, breakdown("100.00", "200.00", "0.00"));
        }
        when(registerSessionRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(sessions);
        when(registerSessionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(sessions.subList(10, 20), PageRequest.of(1, 10), 20));
        when(cashLedgerService.breakdowns(sessions)).thenReturn(breakdowns);

        RegisterReportResponse response = service.summarize(new RegisterReportRequest(
                STORE_ID, REGISTER_ID, CASHIER_ID, RegisterSessionStatus.CLOSED,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-20"), 1, 10),
                mock(org.springframework.security.core.Authentication.class));

        assertThat(response.rows().page()).isEqualTo(1);
        assertThat(response.rows().size()).isEqualTo(10);
        assertThat(response.rows().content()).extracting(RegisterReportRow::registerSessionId)
                .containsExactlyElementsOf(sessions.subList(10, 20).stream().map(RegisterSession::getId).toList());
        assertThat(response.rows().totalElements()).isEqualTo(20);
        assertThat(response.rows().totalPages()).isEqualTo(2);
        assertThat(response.retailCash()).isEqualByComparingTo("4000.00");
    }

    @Test
    void multipleShiftsUseOnePhysicalOpeningFloatPerRegisterAndBusinessDay() {
        when(actor.getTenantId()).thenReturn(UUID.randomUUID());
        when(storeAccessService.currentTenantUser(any())).thenReturn(actor);
        UUID businessDayId = UUID.randomUUID();
        LocalDate businessDate = LocalDate.parse("2026-09-30");
        RegisterSession firstShift = session(UUID.randomUUID(), RegisterSessionStatus.CLOSED,
                "700.00", "700.00", businessDate, businessDayId);
        RegisterSession secondShift = session(UUID.randomUUID(), RegisterSessionStatus.CLOSED,
                "600.00", "600.00", businessDate, businessDayId);
        when(firstShift.getOpenedAt()).thenReturn(Instant.parse("2026-09-30T08:00:00Z"));
        when(secondShift.getOpenedAt()).thenReturn(Instant.parse("2026-09-30T16:00:00Z"));
        List<RegisterSession> sessions = List.of(firstShift, secondShift);
        when(registerSessionRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(sessions);
        when(registerSessionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(sessions, PageRequest.of(0, 5), 2));
        UUID firstShiftId = firstShift.getId();
        UUID secondShiftId = secondShift.getId();
        when(cashLedgerService.breakdowns(sessions)).thenReturn(Map.of(
                firstShiftId, breakdown("440.00", "260.00", "0.00"),
                secondShiftId, breakdown("440.00", "160.00", "0.00")));

        RegisterBusinessDayCashState state = mock(RegisterBusinessDayCashState.class);
        RegisterBusinessDayCashStateRepository states = mock(RegisterBusinessDayCashStateRepository.class);
        BusinessDay reportDay = firstShift.getBusinessDay();
        Register reportRegister = firstShift.getRegister();
        when(state.getBusinessDay()).thenReturn(reportDay);
        when(state.getRegister()).thenReturn(reportRegister);
        when(state.getInitialFloat()).thenReturn(new BigDecimal("440.00"));
        when(states.findAllByBusinessDay_IdIn(java.util.Set.of(businessDayId))).thenReturn(List.of(state));
        ReflectionTestUtils.setField(service, "registerBusinessDayCashStateRepository", states);

        RegisterReportResponse response = service.summarize(new RegisterReportRequest(
                STORE_ID, REGISTER_ID, null, null, businessDate, businessDate, 0, 5),
                mock(org.springframework.security.core.Authentication.class));

        assertThat(response.sessionCount()).isEqualTo(2);
        assertThat(response.openingCash()).isEqualByComparingTo("440.00");
        assertThat(response.rows().content()).extracting(RegisterReportRow::openingCash)
                .containsExactly(new BigDecimal("440.00"), new BigDecimal("440.00"));
    }

    private static CashLedgerBreakdownResponse breakdown() {
        return new CashLedgerBreakdownResponse(
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                new BigDecimal("30.00"),
                new BigDecimal("12.00"),
                new BigDecimal("80.00"),
                new BigDecimal("25.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                new BigDecimal("40.00"),
                new BigDecimal("15.00"),
                new BigDecimal("375.00"),
                new BigDecimal("92.00"),
                new BigDecimal("383.00"),
                List.of());
    }

    private static CashLedgerBreakdownResponse breakdown(String opening, String cashIn, String cashOut) {
        BigDecimal openingAmount = new BigDecimal(opening);
        BigDecimal in = new BigDecimal(cashIn);
        BigDecimal out = new BigDecimal(cashOut);
        return new CashLedgerBreakdownResponse(openingAmount, in, BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), out,
                in, out, openingAmount.add(in).subtract(out), List.of());
    }

    private static RegisterSession session() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        when(store.getCurrencyCode()).thenReturn("USD");

        Register register = mock(Register.class);
        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getCode()).thenReturn("R1");
        when(register.getName()).thenReturn("Front Register");

        User cashier = mock(User.class);
        when(cashier.getId()).thenReturn(CASHIER_ID);
        when(cashier.getEmail()).thenReturn("cashier@example.local");
        when(cashier.getDisplayName()).thenReturn("Ada Cashier");

        RegisterSession session = mock(RegisterSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStore()).thenReturn(store);
        when(session.getRegister()).thenReturn(register);
        when(session.getAssignedCashier()).thenReturn(cashier);
        when(session.getStatus()).thenReturn(RegisterSessionStatus.CLOSED);
        when(session.getCountedCash()).thenReturn(new BigDecimal("380.00"));
        when(session.getExpectedCashAtClose()).thenReturn(new BigDecimal("383.00"));
        when(session.getDifferenceCash()).thenReturn(new BigDecimal("-3.00"));
        when(session.getOpenedAt()).thenReturn(Instant.parse("2026-07-29T08:00:00Z"));
        when(session.getClosedAt()).thenReturn(Instant.parse("2026-07-29T16:00:00Z"));
        return session;
    }


    private static RegisterSession session(UUID id, RegisterSessionStatus status, String counted,
                                           String expectedAtClose, LocalDate businessDate) {
        return session(id, status, counted, expectedAtClose, businessDate, UUID.randomUUID());
    }

    private static RegisterSession session(UUID id, RegisterSessionStatus status, String counted,
                                           String expectedAtClose, LocalDate businessDate, UUID businessDayId) {
        RegisterSession session = session();
        when(session.getId()).thenReturn(id);
        when(session.getStatus()).thenReturn(status);
        when(session.getCountedCash()).thenReturn(counted == null ? null : new BigDecimal(counted));
        when(session.getExpectedCashAtClose()).thenReturn(expectedAtClose == null ? null : new BigDecimal(expectedAtClose));
        when(session.getDifferenceCash()).thenReturn(counted == null || expectedAtClose == null ? null
                : new BigDecimal(counted).subtract(new BigDecimal(expectedAtClose)));
        BusinessDay day = mock(BusinessDay.class);
        when(day.getId()).thenReturn(businessDayId);
        when(day.getBusinessDate()).thenReturn(businessDate);
        when(session.getBusinessDay()).thenReturn(day);
        return session;
    }
}
