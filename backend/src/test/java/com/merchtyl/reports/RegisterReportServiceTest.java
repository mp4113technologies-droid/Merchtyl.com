package com.merchtyl.reports;

import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
    private final RegisterReportService service = new RegisterReportService(
            registerSessionRepository,
            cashLedgerService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void summarizesRegisterCashBucketsAndVariance() {
        RegisterSession session = session();
        when(registerSessionRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(session));
        when(cashLedgerService.breakdowns(List.of(session))).thenReturn(Map.of(SESSION_ID, breakdown()));

        RegisterReportResponse response = service.summarize(new RegisterReportRequest(
                STORE_ID,
                REGISTER_ID,
                CASHIER_ID,
                RegisterSessionStatus.CLOSED,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31")));

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
        assertThat(response.rows()).singleElement().satisfies(row -> {
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
        when(session.getDifferenceCash()).thenReturn(new BigDecimal("-3.00"));
        when(session.getOpenedAt()).thenReturn(Instant.parse("2026-07-29T08:00:00Z"));
        when(session.getClosedAt()).thenReturn(Instant.parse("2026-07-29T16:00:00Z"));
        return session;
    }
}
