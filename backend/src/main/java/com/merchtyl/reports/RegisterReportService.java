package com.merchtyl.reports;

import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.sales.SalesClassification;
import com.merchtyl.sales.SalesClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RegisterReportService {
    private static final int MONEY_SCALE = 2;

    private final RegisterSessionRepository registerSessionRepository;
    private final CashLedgerService cashLedgerService;
    private final StoreAccessService storeAccessService;
    private final Clock clock;
    @Autowired(required = false)
    private SalesClassificationService salesClassificationService;

    @Autowired
    public RegisterReportService(
            RegisterSessionRepository registerSessionRepository,
            CashLedgerService cashLedgerService,
            StoreAccessService storeAccessService) {
        this(registerSessionRepository, cashLedgerService, storeAccessService, Clock.systemUTC());
    }

    RegisterReportService(
            RegisterSessionRepository registerSessionRepository,
            CashLedgerService cashLedgerService,
            StoreAccessService storeAccessService,
            Clock clock) {
        this.registerSessionRepository = registerSessionRepository;
        this.cashLedgerService = cashLedgerService;
        this.storeAccessService = storeAccessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RegisterReportResponse summarize(RegisterReportRequest request, Authentication authentication) {
        RegisterReportRequest filters = normalize(request);
        User actor = storeAccessService.currentTenantUser(authentication);
        if (filters.storeId() != null) {
            storeAccessService.requireStoreAccess(authentication, filters.storeId());
        }
        Set<UUID> accessibleStoreIds = filters.storeId() == null
                ? storeAccessService.assignedStores(authentication).stream()
                        .map(value -> value.storeId()).collect(Collectors.toSet())
                : Set.of(filters.storeId());
        List<RegisterSession> sessions = registerSessionRepository
                .findAll(specification(filters, actor, accessibleStoreIds),
                        Sort.by(Sort.Direction.DESC, "openedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<RegisterSession> sessionPage = registerSessionRepository.findAll(
                specification(filters, actor, accessibleStoreIds),
                PageRequest.of(filters.page(), filters.size(),
                        Sort.by(Sort.Direction.DESC, "openedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<UUID, CashLedgerBreakdownResponse> breakdowns = cashLedgerService.breakdowns(sessions);
        Map<UUID, SalesClassification> classifications = salesClassificationService == null
                ? Map.of()
                : salesClassificationService.byRegisterSessions(actor.getTenantId(), sessions.stream().map(RegisterSession::getId).toList());
        List<RegisterReportRow> rows = sessions.stream()
                .map(session -> row(session, breakdowns.get(session.getId()), classifications.getOrDefault(session.getId(), SalesClassification.zero())))
                .toList();
        List<RegisterReportRow> reconciledRows = rows.stream()
                .filter(RegisterReportService::isReconciled)
                .toList();
        List<RegisterReportRow> pageRows = sessionPage.getContent().stream()
                .map(session -> row(session, breakdowns.get(session.getId()), classifications.getOrDefault(session.getId(), SalesClassification.zero())))
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
                sum(rows, RegisterReportRow::cashOut),
                sum(reconciledRows, RegisterReportRow::expectedCash),
                sum(reconciledRows, RegisterReportRow::countedCash),
                sum(reconciledRows, RegisterReportRow::variance),
                sum(rows, RegisterReportRow::taxableSales),
                sum(rows, RegisterReportRow::nonTaxableSales),
                sum(rows, RegisterReportRow::generalTaxCollected),
                sum(rows, RegisterReportRow::vapeTaxCollected),
                sum(rows, RegisterReportRow::totalTaxCollected),
                sum(rows, RegisterReportRow::merchandiseNetSales),
                rows.size(),
                reconciledRows.size(),
                rows.stream().filter(row -> row.status() == com.merchtyl.registersession.RegisterSessionStatus.OPEN
                        || row.status() == com.merchtyl.registersession.RegisterSessionStatus.CLOSING).count(),
                new PageResponse<>(pageRows, sessionPage.getNumber(), sessionPage.getSize(),
                        sessionPage.getTotalElements(), sessionPage.getTotalPages(),
                        sessionPage.isFirst(), sessionPage.isLast()),
                Instant.now(clock));
    }

    private RegisterReportRow row(RegisterSession session, CashLedgerBreakdownResponse breakdown, SalesClassification classification) {
        BigDecimal retailCash = money(breakdown.retailCashReceived().subtract(breakdown.retailChange()));
        BigDecimal lotteryCash = money(breakdown.lotteryCashSales()
                .add(breakdown.payoutReversals())
                .subtract(breakdown.lotteryPayouts())
                .subtract(breakdown.lotterySaleCancellations()));
        BigDecimal cashMovements = money(breakdown.otherCashIn().subtract(breakdown.otherCashOut()));
        boolean reconciled = session.getCountedCash() != null && session.getExpectedCashAtClose() != null;
        BigDecimal expectedCash = reconciled ? money(session.getExpectedCashAtClose()) : money(breakdown.expectedCash());
        BigDecimal countedCash = reconciled ? money(session.getCountedCash()) : null;
        BigDecimal variance = reconciled ? money(countedCash.subtract(expectedCash)) : null;
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
                session.getBusinessDay() == null ? null : session.getBusinessDay().getBusinessDate(),
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
                money(breakdown.totalIn()),
                money(breakdown.totalOut()),
                expectedCash,
                countedCash,
                variance,
                classification.taxableSales(),
                classification.nonTaxableSales(),
                classification.generalTaxCollected(),
                classification.vapeTaxCollected(),
                classification.totalTaxCollected(),
                classification.merchandiseNetSales(),
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
        if (request.page() < 0) {
            throw new BadRequestException("page must be zero or greater");
        }
        if (request.size() != 5 && request.size() != 10) {
            throw new BadRequestException("size must be 5 or 10");
        }
        return request;
    }

    private static Specification<RegisterSession> specification(
            RegisterReportRequest request, User actor, Set<UUID> accessibleStoreIds) {
        return Specification.<RegisterSession>where((root, query, criteriaBuilder) -> criteriaBuilder.equal(
                        root.get("store").get("tenantId"), actor.getTenantId()))
                .and((root, query, criteriaBuilder) -> root.get("store").get("id").in(accessibleStoreIds))
                .and(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("assignedCashier", request.cashierId()))
                .and(equalEnum("status", request.status()))
                .and(businessDateGreaterThanOrEqualTo(request.dateFrom()))
                .and(businessDateLessThanOrEqualTo(request.dateTo()));
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

    private static Specification<RegisterSession> businessDateGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant start = value.atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.greaterThanOrEqualTo(root.get("businessDay").get("businessDate"), value),
                criteriaBuilder.and(criteriaBuilder.isNull(root.get("businessDay")),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("openedAt"), start)));
    }

    private static Specification<RegisterSession> businessDateLessThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant end = value.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.lessThanOrEqualTo(root.get("businessDay").get("businessDate"), value),
                criteriaBuilder.and(criteriaBuilder.isNull(root.get("businessDay")),
                        criteriaBuilder.lessThan(root.get("openedAt"), end)));
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

    private static boolean isReconciled(RegisterReportRow row) {
        return row.countedCash() != null && row.variance() != null
                && (row.status() == com.merchtyl.registersession.RegisterSessionStatus.CLOSED
                || row.status() == com.merchtyl.registersession.RegisterSessionStatus.FORCE_CLOSED);
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
