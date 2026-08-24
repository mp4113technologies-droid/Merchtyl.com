package com.merchtyl.cash;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Currency;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CashLedgerService {
    private static final int MONEY_SCALE = 2;

    private final CashLedgerRepository cashLedgerRepository;

    public CashLedgerService(CashLedgerRepository cashLedgerRepository) {
        this.cashLedgerRepository = cashLedgerRepository;
    }

    @Transactional
    public CashLedgerEntryResponse append(CashLedgerEntryCommand command) {
        CashLedgerEntryCommand normalized = normalize(command);
        CashLedgerEntry entry = new CashLedgerEntry(normalized);
        return CashLedgerEntryResponse.from(save(entry));
    }

    @Transactional
    public CashLedgerEntryResponse appendOpeningFloat(RegisterSession session, User cashier) {
        Store store = session.getStore();
        return append(new CashLedgerEntryCommand(
                store,
                session.getRegister(),
                session,
                CashLedgerSourceType.SESSION_OPENING_FLOAT,
                session.getId(),
                CashLedgerDirection.IN,
                session.getOpeningCash(),
                store.getCurrencyCode(),
                businessDate(store, session),
                session.getOpenedAt(),
                cashier,
                session.getId(),
                "Register session opening float"));
    }

    @Transactional(readOnly = true)
    public BigDecimal expectedCash(UUID registerSessionId) {
        if (registerSessionId == null) {
            throw new BadRequestException("registerSessionId is required");
        }
        return cashLedgerRepository.calculateExpectedCash(registerSessionId).setScale(MONEY_SCALE);
    }

    @Transactional(readOnly = true)
    public BigDecimal expectedCash(RegisterSession session) {
        if (session == null) {
            throw new BadRequestException("registerSession is required");
        }
        return expectedCash(session.getId());
    }

    @Transactional(readOnly = true)
    public CashLedgerBreakdownResponse breakdown(RegisterSession session) {
        if (session == null) {
            throw new BadRequestException("registerSession is required");
        }
        List<CashLedgerEntry> entries = cashLedgerRepository.findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(session.getId());
        return breakdown(session, entries);
    }

    @Transactional(readOnly = true)
    public Map<UUID, CashLedgerBreakdownResponse> breakdowns(Collection<RegisterSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return Map.of();
        }
        List<UUID> sessionIds = sessions.stream()
                .map(RegisterSession::getId)
                .toList();
        Map<UUID, List<CashLedgerEntry>> entriesBySessionId = cashLedgerRepository
                .findByRegisterSession_IdInOrderByRegisterSession_IdAscOccurredAtAscCreatedAtAsc(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getRegisterSession().getId()));
        Map<UUID, CashLedgerBreakdownResponse> breakdowns = new LinkedHashMap<>();
        for (RegisterSession session : sessions) {
            breakdowns.put(session.getId(), breakdown(
                    session,
                    entriesBySessionId.getOrDefault(session.getId(), List.of())));
        }
        return breakdowns;
    }

    private static CashLedgerBreakdownResponse breakdown(RegisterSession session, List<CashLedgerEntry> entries) {
        BigDecimal retailCashReceived = total(entries, CashLedgerSourceType.SALE_CASH_RECEIPT, CashLedgerDirection.IN);
        BigDecimal retailChange = total(entries, CashLedgerSourceType.SALE_CHANGE_GIVEN, CashLedgerDirection.OUT);
        BigDecimal retailRefunds = total(entries, CashLedgerSourceType.CASH_REFUND, CashLedgerDirection.OUT);
        BigDecimal lotteryCashSales = total(entries, CashLedgerSourceType.LOTTERY_SALE_CASH, CashLedgerDirection.IN);
        BigDecimal lotteryPayouts = total(entries, CashLedgerSourceType.LOTTERY_PAYOUT_CASH, CashLedgerDirection.OUT);
        BigDecimal payoutReversals = total(entries, CashLedgerSourceType.LOTTERY_PAYOUT_REVERSAL, CashLedgerDirection.IN);
        BigDecimal lotterySaleCancellations = total(entries, CashLedgerSourceType.LOTTERY_SALE_CANCELLATION_CASH, CashLedgerDirection.OUT);
        BigDecimal otherCashIn = totalOther(entries, CashLedgerDirection.IN);
        BigDecimal otherCashOut = totalOther(entries, CashLedgerDirection.OUT);
        BigDecimal totalIn = entries.stream()
                .filter(entry -> entry.getDirection() == CashLedgerDirection.IN)
                .filter(entry -> entry.getSourceType() != CashLedgerSourceType.SESSION_OPENING_FLOAT)
                .map(CashLedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE);
        BigDecimal totalOut = entries.stream()
                .filter(entry -> entry.getDirection() == CashLedgerDirection.OUT)
                .map(CashLedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE);
        BigDecimal expectedCash = session.getOpeningCash()
                .add(totalIn)
                .subtract(totalOut)
                .setScale(MONEY_SCALE);
        Map<SourceDirection, BigDecimal> totals = entries.stream()
                .filter(entry -> entry.getSourceType() != CashLedgerSourceType.SESSION_OPENING_FLOAT)
                .collect(Collectors.groupingBy(
                        entry -> new SourceDirection(entry.getSourceType(), entry.getDirection()),
                        Collectors.mapping(
                                CashLedgerEntry::getAmount,
                                Collectors.reducing(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add))));
        List<CashLedgerSourceBreakdownResponse> sourceBreakdown = totals.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<SourceDirection, BigDecimal> entry) -> entry.getKey().sourceType().name())
                        .thenComparing(entry -> entry.getKey().direction().name()))
                .map(entry -> new CashLedgerSourceBreakdownResponse(
                        entry.getKey().sourceType(),
                        entry.getKey().direction(),
                        entry.getValue().setScale(MONEY_SCALE)))
                .toList();
        return new CashLedgerBreakdownResponse(
                session.getOpeningCash().setScale(MONEY_SCALE),
                retailCashReceived,
                retailChange,
                retailRefunds,
                lotteryCashSales,
                lotteryPayouts,
                payoutReversals,
                lotterySaleCancellations,
                otherCashIn,
                otherCashOut,
                totalIn,
                totalOut,
                expectedCash,
                sourceBreakdown);
    }

    private CashLedgerEntry save(CashLedgerEntry entry) {
        try {
            return cashLedgerRepository.saveAndFlush(entry);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Cash ledger entry was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Cash ledger operation already exists");
        }
    }

    private CashLedgerEntryCommand normalize(CashLedgerEntryCommand command) {
        if (command == null) {
            throw new BadRequestException("cash ledger entry is required");
        }
        requireRelationships(command);
        CashLedgerSourceType sourceType = requireNonNull(command.sourceType(), "sourceType");
        CashLedgerDirection direction = requireNonNull(command.direction(), "direction");
        validateSourceDirection(sourceType, direction);
        UUID sourceId = requireNonNull(command.sourceId(), "sourceId");
        UUID operationId = requireNonNull(command.operationId(), "operationId");
        if (cashLedgerRepository.existsByOperationId(operationId)) {
            throw new ConflictException("Cash ledger operation already exists");
        }
        return new CashLedgerEntryCommand(
                command.store(),
                command.register(),
                command.registerSession(),
                sourceType,
                sourceId,
                direction,
                normalizeAmount(command.amount()),
                normalizeCurrencyCode(command.currencyCode()),
                requireNonNull(command.businessDate(), "businessDate"),
                requireNonNull(command.occurredAt(), "occurredAt"),
                command.createdBy(),
                operationId,
                cleanOptional(command.notes()));
    }

    private static void requireRelationships(CashLedgerEntryCommand command) {
        if (command.store() == null) {
            throw new BadRequestException("store is required");
        }
        if (command.register() == null) {
            throw new BadRequestException("register is required");
        }
        if (command.registerSession() == null) {
            throw new BadRequestException("registerSession is required");
        }
        if (command.createdBy() == null) {
            throw new BadRequestException("createdBy is required");
        }
        UUID storeId = command.store().getId();
        UUID registerId = command.register().getId();
        if (!command.register().getStore().getId().equals(storeId)) {
            throw new BadRequestException("register must belong to store");
        }
        if (!command.registerSession().getStore().getId().equals(storeId)) {
            throw new BadRequestException("registerSession must belong to store");
        }
        if (!command.registerSession().getRegister().getId().equals(registerId)) {
            throw new BadRequestException("registerSession must belong to register");
        }
    }

    private static void validateSourceDirection(CashLedgerSourceType sourceType, CashLedgerDirection direction) {
        switch (sourceType) {
            case SESSION_OPENING_FLOAT, SALE_CASH_RECEIPT, LOTTERY_SALE_CASH, LOTTERY_PAYOUT_REVERSAL -> requireDirection(sourceType, direction, CashLedgerDirection.IN);
            case SALE_CHANGE_GIVEN, LOTTERY_PAYOUT_CASH, LOTTERY_SALE_CANCELLATION_CASH, CASH_REFUND -> requireDirection(sourceType, direction, CashLedgerDirection.OUT);
            case CASH_MOVEMENT, SESSION_CLOSE_ADJUSTMENT -> {
            }
        }
    }

    private static BigDecimal total(
            List<CashLedgerEntry> entries,
            CashLedgerSourceType sourceType,
            CashLedgerDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getSourceType() == sourceType)
                .filter(entry -> entry.getDirection() == direction)
                .map(CashLedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE);
    }

    private static BigDecimal totalOther(List<CashLedgerEntry> entries, CashLedgerDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .filter(entry -> entry.getSourceType() == CashLedgerSourceType.CASH_MOVEMENT
                        || entry.getSourceType() == CashLedgerSourceType.SESSION_CLOSE_ADJUSTMENT)
                .map(CashLedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE);
    }

    private static void requireDirection(
            CashLedgerSourceType sourceType,
            CashLedgerDirection actual,
            CashLedgerDirection expected) {
        if (actual != expected) {
            throw new BadRequestException(sourceType.name() + " requires direction " + expected.name());
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("amount is required");
        }
        if (amount.signum() <= 0) {
            throw new BadRequestException("amount must be greater than 0.00");
        }
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("amount may include no more than 2 decimal places");
        }
    }

    private static String normalizeCurrencyCode(String value) {
        String currencyCode = cleanRequired(value, "currencyCode").toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("currencyCode must be a valid ISO 4217 code");
        }
        return currencyCode;
    }

    private static LocalDate businessDate(Store store, RegisterSession session) {
        return session.getOpenedAt().atZone(ZoneId.of(store.getTimezone())).toLocalDate();
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private record SourceDirection(CashLedgerSourceType sourceType, CashLedgerDirection direction) {
    }
}
