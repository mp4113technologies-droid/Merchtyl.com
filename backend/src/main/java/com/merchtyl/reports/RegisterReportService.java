package com.merchtyl.reports;

import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RegisterReportService {
    private static final int MONEY_SCALE = 2;

    private final RegisterSessionRepository registerSessionRepository;
    private final CashLedgerService cashLedgerService;
    private final Clock clock;

    @Autowired
    public RegisterReportService(
            RegisterSessionRepository registerSessionRepository,
            CashLedgerService cashLedgerService) {
        this(registerSessionRepository, cashLedgerService, Clock.systemUTC());
    }

    RegisterReportService(
            RegisterSessionRepository registerSessionRepository,
            CashLedgerService cashLedgerService,
            Clock clock) {
        this.registerSessionRepository = registerSessionRepository;
        this.cashLedgerService = cashLedgerService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RegisterReportResponse summarize(RegisterReportRequest request) {
        RegisterReportRequest filters = normalize(request);
        List<RegisterSession> sessions = registerSessionRepository
                .findAll(specification(filters),
                        Sort.by(Sort.Direction.DESC, "openedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Map<UUID, CashLedgerBreakdownResponse> breakdowns = cashLedgerService.breakdowns(sessions);
        List<RegisterReportRow> rows = sessions.stream()
                .map(session -> row(session, breakdowns.get(session.getId())))
                .toList();

        return new RegisterReportResponse(
                filters.storeId(),
                filters.registerId(),
                filters.cashierId(),
                filters.status(),
                filters.dateFrom(),
                filters.dateTo(),
                sum(rows, RegisterReportRow::openingCash),
                sum(rows, RegisterReportRow::retailCash),
                sum(rows, RegisterReportRow::retailCashReceived),
                sum(rows, RegisterReportRow::retailChange),
                sum(rows, RegisterReportRow::lotteryCash),
                sum(rows, RegisterReportRow::lotteryCashSales),
                sum(rows, RegisterReportRow::lotteryPayouts),
                sum(rows, RegisterReportRow::payoutReversals),
                sum(rows, RegisterReportRow::lotterySaleCancellations),
                sum(rows, RegisterReportRow::refunds),
                sum(rows, RegisterReportRow::cashMovements),
                sum(rows, RegisterReportRow::cashMovementIn),
                sum(rows, RegisterReportRow::cashMovementOut),
                sum(rows, RegisterReportRow::expectedCash),
                sum(rows, RegisterReportRow::countedCash),
                sum(rows, RegisterReportRow::variance),
                rows.size(),
                rows.stream().filter(row -> row.countedCash() != null).count(),
                rows,
                Instant.now(clock));
    }

    private RegisterReportRow row(RegisterSession session, CashLedgerBreakdownResponse breakdown) {
        BigDecimal retailCash = money(breakdown.retailCashReceived().subtract(breakdown.retailChange()));
        BigDecimal lotteryCash = money(breakdown.lotteryCashSales()
                .add(breakdown.payoutReversals())
                .subtract(breakdown.lotteryPayouts())
                .subtract(breakdown.lotterySaleCancellations()));
        BigDecimal cashMovements = money(breakdown.otherCashIn().subtract(breakdown.otherCashOut()));
        return new RegisterReportRow(
                session.getId(),
                session.getStore().getId(),
                session.getStore().getCode(),
                session.getStore().getName(),
                session.getRegister().getId(),
                session.getRegister().getCode(),
                session.getRegister().getName(),
                session.getAssignedCashier().getId(),
                session.getAssignedCashier().getEmail(),
                session.getAssignedCashier().getDisplayName(),
                session.getStatus(),
                session.getStore().getCurrencyCode(),
                money(breakdown.openingCash()),
                retailCash,
                money(breakdown.retailCashReceived()),
                money(breakdown.retailChange()),
                lotteryCash,
                money(breakdown.lotteryCashSales()),
                money(breakdown.lotteryPayouts()),
                money(breakdown.payoutReversals()),
                money(breakdown.lotterySaleCancellations()),
                money(breakdown.retailRefunds()),
                cashMovements,
                money(breakdown.otherCashIn()),
                money(breakdown.otherCashOut()),
                money(breakdown.expectedCash()),
                moneyOrNull(session.getCountedCash()),
                moneyOrNull(session.getDifferenceCash()),
                session.getOpenedAt(),
                session.getClosedAt());
    }

    private static RegisterReportRequest normalize(RegisterReportRequest request) {
        if (request == null) {
            throw new BadRequestException("register report request is required");
        }
        if (request.dateFrom() != null && request.dateTo() != null && request.dateTo().isBefore(request.dateFrom())) {
            throw new BadRequestException("dateTo must be on or after dateFrom");
        }
        return request;
    }

    private static Specification<RegisterSession> specification(RegisterReportRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("assignedCashier", request.cashierId()))
                .and(equalEnum("status", request.status()))
                .and(openedAtGreaterThanOrEqualTo(request.dateFrom()))
                .and(openedAtBeforeDayAfter(request.dateTo()));
    }

    private static Specification<RegisterSession> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<RegisterSession> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<RegisterSession> openedAtGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant start = value.atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("openedAt"), start);
    }

    private static Specification<RegisterSession> openedAtBeforeDayAfter(LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant end = value.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("openedAt"), end);
    }

    private static BigDecimal sum(List<RegisterReportRow> rows, AmountSelector selector) {
        return money(rows.stream()
                .map(selector::amount)
                .filter(value -> value != null)
                .reduce(moneyZero(), BigDecimal::add));
    }

    private static BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return moneyZero();
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @FunctionalInterface
    private interface AmountSelector {
        BigDecimal amount(RegisterReportRow row);
    }
}
