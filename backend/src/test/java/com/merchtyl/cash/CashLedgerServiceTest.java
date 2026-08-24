package com.merchtyl.cash;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashLedgerServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID OPERATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000905");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-27T12:00:00Z");

    private final CashLedgerRepository cashLedgerRepository = mock(CashLedgerRepository.class);
    private final CashLedgerService service = new CashLedgerService(cashLedgerRepository);

    private Store store;
    private Register register;
    private RegisterSession session;
    private User cashier;

    @BeforeEach
    void setUp() {
        store = mock(Store.class);
        register = mock(Register.class);
        session = mock(RegisterSession.class);
        cashier = new User("cashier@example.local", "Cashier One", "hash");

        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCurrencyCode()).thenReturn("USD");
        when(store.getTimezone()).thenReturn("America/Los_Angeles");
        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getStore()).thenReturn(store);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStore()).thenReturn(store);
        when(session.getRegister()).thenReturn(register);
        when(session.getOpeningCash()).thenReturn(new BigDecimal("125.50"));
        when(session.getOpenedAt()).thenReturn(OCCURRED_AT);
        when(cashLedgerRepository.saveAndFlush(any(CashLedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void appendsLedgerEntryWithNormalizedMoneyAndMetadata() {
        CashLedgerEntryResponse response = service.append(command(
                CashLedgerSourceType.SALE_CASH_RECEIPT,
                CashLedgerDirection.IN,
                new BigDecimal("20.00")));

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.registerId()).isEqualTo(REGISTER_ID);
        assertThat(response.registerSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.sourceType()).isEqualTo(CashLedgerSourceType.SALE_CASH_RECEIPT);
        assertThat(response.direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(response.amount()).isEqualByComparingTo("20.00");
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.businessDate()).isEqualTo(LocalDate.parse("2026-07-27"));
        assertThat(response.createdBy()).isEqualTo(cashier.getId());
    }

    @Test
    void openingFloatUsesSessionAsSourceAndOperation() {
        CashLedgerEntryResponse response = service.appendOpeningFloat(session, cashier);

        assertThat(response.sourceType()).isEqualTo(CashLedgerSourceType.SESSION_OPENING_FLOAT);
        assertThat(response.sourceId()).isEqualTo(SESSION_ID);
        assertThat(response.operationId()).isEqualTo(SESSION_ID);
        assertThat(response.direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(response.amount()).isEqualByComparingTo("125.50");
    }

    @Test
    void expectedCashIsCalculatedFromRepositoryLedgerSum() {
        when(cashLedgerRepository.calculateExpectedCash(SESSION_ID)).thenReturn(new BigDecimal("87.75"));

        assertThat(service.expectedCash(SESSION_ID)).isEqualByComparingTo("87.75");
    }

    @Test
    void breakdownSeparatesOpeningCashFromLedgerMovementTotals() {
        CashLedgerEntry opening = new CashLedgerEntry(command(
                CashLedgerSourceType.SESSION_OPENING_FLOAT,
                CashLedgerDirection.IN,
                new BigDecimal("125.50")));
        CashLedgerEntry receipt = new CashLedgerEntry(command(
                CashLedgerSourceType.SALE_CASH_RECEIPT,
                CashLedgerDirection.IN,
                new BigDecimal("20.00")));
        CashLedgerEntry cashOut = new CashLedgerEntry(command(
                CashLedgerSourceType.CASH_MOVEMENT,
                CashLedgerDirection.OUT,
                new BigDecimal("5.00")));
        when(cashLedgerRepository.findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(SESSION_ID))
                .thenReturn(List.of(opening, receipt, cashOut));

        CashLedgerBreakdownResponse breakdown = service.breakdown(session);

        assertThat(breakdown.openingCash()).isEqualByComparingTo("125.50");
        assertThat(breakdown.totalIn()).isEqualByComparingTo("20.00");
        assertThat(breakdown.totalOut()).isEqualByComparingTo("5.00");
        assertThat(breakdown.expectedCash()).isEqualByComparingTo("140.50");
        assertThat(breakdown.sourceBreakdown()).extracting(CashLedgerSourceBreakdownResponse::sourceType)
                .containsExactly(CashLedgerSourceType.CASH_MOVEMENT, CashLedgerSourceType.SALE_CASH_RECEIPT);
    }

    @Test
    void breakdownBalancesRetailLotteryAndOtherCashActivitySeparately() {
        when(session.getOpeningCash()).thenReturn(new BigDecimal("100.00"));
        when(cashLedgerRepository.findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(SESSION_ID))
                .thenReturn(List.of(
                        entry(CashLedgerSourceType.SESSION_OPENING_FLOAT, CashLedgerDirection.IN, "100.00"),
                        entry(CashLedgerSourceType.SALE_CASH_RECEIPT, CashLedgerDirection.IN, "50.00"),
                        entry(CashLedgerSourceType.SALE_CHANGE_GIVEN, CashLedgerDirection.OUT, "5.00"),
                        entry(CashLedgerSourceType.CASH_REFUND, CashLedgerDirection.OUT, "7.00"),
                        entry(CashLedgerSourceType.LOTTERY_SALE_CASH, CashLedgerDirection.IN, "20.00"),
                        entry(CashLedgerSourceType.LOTTERY_PAYOUT_CASH, CashLedgerDirection.OUT, "30.00"),
                        entry(CashLedgerSourceType.LOTTERY_PAYOUT_REVERSAL, CashLedgerDirection.IN, "10.00"),
                        entry(CashLedgerSourceType.LOTTERY_SALE_CANCELLATION_CASH, CashLedgerDirection.OUT, "8.00"),
                        entry(CashLedgerSourceType.CASH_MOVEMENT, CashLedgerDirection.IN, "12.00"),
                        entry(CashLedgerSourceType.CASH_MOVEMENT, CashLedgerDirection.OUT, "6.00")));

        CashLedgerBreakdownResponse breakdown = service.breakdown(session);

        assertThat(breakdown.openingCash()).isEqualByComparingTo("100.00");
        assertThat(breakdown.retailCashReceived()).isEqualByComparingTo("50.00");
        assertThat(breakdown.retailChange()).isEqualByComparingTo("5.00");
        assertThat(breakdown.retailRefunds()).isEqualByComparingTo("7.00");
        assertThat(breakdown.lotteryCashSales()).isEqualByComparingTo("20.00");
        assertThat(breakdown.lotteryPayouts()).isEqualByComparingTo("30.00");
        assertThat(breakdown.payoutReversals()).isEqualByComparingTo("10.00");
        assertThat(breakdown.lotterySaleCancellations()).isEqualByComparingTo("8.00");
        assertThat(breakdown.otherCashIn()).isEqualByComparingTo("12.00");
        assertThat(breakdown.otherCashOut()).isEqualByComparingTo("6.00");
        assertThat(breakdown.expectedCash()).isEqualByComparingTo("136.00");
    }

    @Test
    void bulkBreakdownsReadLedgerEntriesOnceForAllSessions() {
        UUID secondSessionId = UUID.fromString("00000000-0000-0000-0000-000000000906");
        RegisterSession secondSession = mock(RegisterSession.class);
        when(secondSession.getId()).thenReturn(secondSessionId);
        when(secondSession.getOpeningCash()).thenReturn(new BigDecimal("25.00"));

        CashLedgerEntry firstReceipt = new CashLedgerEntry(command(
                session,
                CashLedgerSourceType.SALE_CASH_RECEIPT,
                CashLedgerDirection.IN,
                new BigDecimal("20.00")));
        CashLedgerEntry secondPayout = new CashLedgerEntry(command(
                secondSession,
                CashLedgerSourceType.LOTTERY_PAYOUT_CASH,
                CashLedgerDirection.OUT,
                new BigDecimal("5.00")));
        when(cashLedgerRepository.findByRegisterSession_IdInOrderByRegisterSession_IdAscOccurredAtAscCreatedAtAsc(
                List.of(SESSION_ID, secondSessionId)))
                .thenReturn(List.of(firstReceipt, secondPayout));

        var breakdowns = service.breakdowns(List.of(session, secondSession));

        assertThat(breakdowns).containsOnlyKeys(SESSION_ID, secondSessionId);
        assertThat(breakdowns.get(SESSION_ID).expectedCash()).isEqualByComparingTo("145.50");
        assertThat(breakdowns.get(secondSessionId).expectedCash()).isEqualByComparingTo("20.00");
    }

    @Test
    void sourceTypesEnforceExpectedDirection() {
        assertThatThrownBy(() -> service.append(command(
                CashLedgerSourceType.SALE_CHANGE_GIVEN,
                CashLedgerDirection.IN,
                new BigDecimal("5.00"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("SALE_CHANGE_GIVEN requires direction OUT");

        verify(cashLedgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void lotterySaleCancellationRequiresCashOutDirection() {
        assertThatThrownBy(() -> service.append(command(
                CashLedgerSourceType.LOTTERY_SALE_CANCELLATION_CASH,
                CashLedgerDirection.IN,
                new BigDecimal("8.00"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("LOTTERY_SALE_CANCELLATION_CASH requires direction OUT");

        verify(cashLedgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateOperationIsRejectedBeforeAppend() {
        when(cashLedgerRepository.existsByOperationId(OPERATION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.append(command(
                CashLedgerSourceType.CASH_MOVEMENT,
                CashLedgerDirection.IN,
                new BigDecimal("10.00"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cash ledger operation already exists");

        verify(cashLedgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueOperationViolationIsTranslatedToConflict() {
        when(cashLedgerRepository.saveAndFlush(any(CashLedgerEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate operation"));

        assertThatThrownBy(() -> service.append(command(
                CashLedgerSourceType.CASH_MOVEMENT,
                CashLedgerDirection.IN,
                new BigDecimal("10.00"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cash ledger operation already exists");
    }

    private CashLedgerEntryCommand command(
            CashLedgerSourceType sourceType,
            CashLedgerDirection direction,
            BigDecimal amount) {
        return command(session, sourceType, direction, amount);
    }

    private CashLedgerEntryCommand command(
            RegisterSession registerSession,
            CashLedgerSourceType sourceType,
            CashLedgerDirection direction,
            BigDecimal amount) {
        return new CashLedgerEntryCommand(
                store,
                register,
                registerSession,
                sourceType,
                SOURCE_ID,
                direction,
                amount,
                "usd",
                LocalDate.parse("2026-07-27"),
                OCCURRED_AT,
                cashier,
                OPERATION_ID,
                " Test entry ");
    }

    private CashLedgerEntry entry(CashLedgerSourceType sourceType, CashLedgerDirection direction, String amount) {
        return new CashLedgerEntry(command(sourceType, direction, new BigDecimal(amount)));
    }
}
