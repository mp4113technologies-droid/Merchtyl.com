package com.merchtyl.reports;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.lottery.LotteryPayout;
import com.merchtyl.lottery.LotteryPayoutApproval;
import com.merchtyl.lottery.LotteryPayoutApprovalRepository;
import com.merchtyl.lottery.LotteryPayoutApprovalType;
import com.merchtyl.lottery.LotteryPayoutRepository;
import com.merchtyl.lottery.LotteryPayoutResponse;
import com.merchtyl.lottery.LotteryPayoutReversal;
import com.merchtyl.lottery.LotteryPayoutReversalRepository;
import com.merchtyl.lottery.LotteryPayoutReversalResponse;
import com.merchtyl.lottery.LotteryPayoutStatus;
import com.merchtyl.lottery.LotterySale;
import com.merchtyl.lottery.LotterySaleCancellation;
import com.merchtyl.lottery.LotterySaleCancellationRepository;
import com.merchtyl.lottery.LotterySaleCancellationResponse;
import com.merchtyl.lottery.LotterySaleRepository;
import com.merchtyl.lottery.LotterySaleResponse;
import com.merchtyl.lottery.LotterySaleStatus;
import com.merchtyl.lottery.LotterySettlement;
import com.merchtyl.lottery.LotterySettlementRepository;
import com.merchtyl.lottery.LotterySettlementResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class LotteryReportService {
    private static final int MONEY_SCALE = 2;
    private static final Set<LotterySaleStatus> SETTLEMENT_SALE_STATUSES = Set.of(
            LotterySaleStatus.RECORDED,
            LotterySaleStatus.CANCELLED);
    private static final Set<LotteryPayoutStatus> SETTLEMENT_PAYOUT_STATUSES = Set.of(
            LotteryPayoutStatus.PAID,
            LotteryPayoutStatus.REVERSED);

    private final LotterySaleRepository lotterySaleRepository;
    private final LotteryPayoutRepository lotteryPayoutRepository;
    private final LotteryPayoutApprovalRepository lotteryPayoutApprovalRepository;
    private final LotteryPayoutReversalRepository lotteryPayoutReversalRepository;
    private final LotterySaleCancellationRepository lotterySaleCancellationRepository;
    private final LotterySettlementRepository lotterySettlementRepository;
    private final Clock clock;

    @Autowired
    public LotteryReportService(
            LotterySaleRepository lotterySaleRepository,
            LotteryPayoutRepository lotteryPayoutRepository,
            LotteryPayoutApprovalRepository lotteryPayoutApprovalRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotterySettlementRepository lotterySettlementRepository) {
        this(
                lotterySaleRepository,
                lotteryPayoutRepository,
                lotteryPayoutApprovalRepository,
                lotteryPayoutReversalRepository,
                lotterySaleCancellationRepository,
                lotterySettlementRepository,
                Clock.systemUTC());
    }

    LotteryReportService(
            LotterySaleRepository lotterySaleRepository,
            LotteryPayoutRepository lotteryPayoutRepository,
            LotteryPayoutApprovalRepository lotteryPayoutApprovalRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotterySettlementRepository lotterySettlementRepository,
            Clock clock) {
        this.lotterySaleRepository = lotterySaleRepository;
        this.lotteryPayoutRepository = lotteryPayoutRepository;
        this.lotteryPayoutApprovalRepository = lotteryPayoutApprovalRepository;
        this.lotteryPayoutReversalRepository = lotteryPayoutReversalRepository;
        this.lotterySaleCancellationRepository = lotterySaleCancellationRepository;
        this.lotterySettlementRepository = lotterySettlementRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LotteryReportResponse summarize(LotteryReportRequest request) {
        LotteryReportRequest filters = normalize(request);
        List<LotterySale> sales = lotterySaleRepository.findAll(
                saleSpecification(filters),
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        List<LotteryPayout> payouts = lotteryPayoutRepository.findAll(
                payoutSpecification(filters),
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        List<LotteryPayoutApproval> approvals = lotteryPayoutApprovalRepository.findAll(
                approvalSpecification(filters),
                Sort.by(Sort.Direction.DESC, "approvedAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        List<LotteryPayoutReversal> reversals = lotteryPayoutReversalRepository.findAll(
                reversalSpecification(filters),
                Sort.by(Sort.Direction.DESC, "reversedAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        List<LotterySaleCancellation> cancellations = lotterySaleCancellationRepository.findAll(
                cancellationSpecification(filters),
                Sort.by(Sort.Direction.DESC, "cancelledAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        List<LotterySettlement> settlements = lotterySettlementRepository.findAll(
                settlementSpecification(filters),
                Sort.by(Sort.Direction.DESC, "periodEnd")
                        .and(Sort.by("operator.name"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));

        List<LotteryPayout> referralPayouts = referralPayouts(payouts, approvals);
        BigDecimal salesAmount = money(sales.stream()
                .filter(sale -> SETTLEMENT_SALE_STATUSES.contains(sale.getStatus()))
                .map(LotterySale::getAmount)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal payoutAmount = money(payouts.stream()
                .filter(payout -> SETTLEMENT_PAYOUT_STATUSES.contains(payout.getStatus()))
                .map(LotteryPayout::getAmount)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal approvalAmount = money(approvals.stream()
                .map(LotteryPayoutApproval::getPayoutAmount)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal reversalAmount = money(reversals.stream()
                .map(LotteryPayoutReversal::getAmount)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal referralAmount = money(referralPayouts.stream()
                .map(LotteryPayout::getAmount)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal cancellationAmount = money(cancellations.stream()
                .map(LotterySaleCancellation::getAmount)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal commission = money(settlements.stream()
                .map(LotterySettlement::getCommission)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal calculatedSettlement = money(salesAmount
                .subtract(payoutAmount)
                .subtract(cancellationAmount)
                .add(reversalAmount)
                .subtract(commission));
        BigDecimal settlement = settlements.isEmpty()
                ? calculatedSettlement
                : money(settlements.stream()
                .map(LotterySettlement::getExpectedSettlement)
                .reduce(moneyZero(), BigDecimal::add));
        BigDecimal variance = money(settlement.subtract(calculatedSettlement));

        return new LotteryReportResponse(
                filters.operatorId(),
                filters.storeId(),
                filters.registerId(),
                filters.cashierId(),
                filters.dateFrom(),
                filters.dateTo(),
                salesAmount,
                sales.size(),
                payoutAmount,
                payouts.size(),
                approvalAmount,
                approvals.size(),
                reversalAmount,
                reversals.size(),
                referralAmount,
                referralPayouts.size(),
                cancellationAmount,
                cancellations.size(),
                commission,
                calculatedSettlement,
                settlement,
                variance,
                sales.stream().map(LotterySaleResponse::from).toList(),
                payouts.stream().map(LotteryPayoutResponse::from).toList(),
                approvals.stream().map(LotteryReportService::approvalRow).toList(),
                reversals.stream().map(LotteryPayoutReversalResponse::from).toList(),
                referralPayouts.stream().map(LotteryPayoutResponse::from).toList(),
                cancellations.stream().map(LotterySaleCancellationResponse::from).toList(),
                settlements.stream().map(LotteryReportService::commissionRow).toList(),
                settlements.stream().map(LotterySettlementResponse::from).toList(),
                chartRows(sales, payouts, reversals, referralPayouts, settlements),
                Instant.now(clock));
    }

    private static LotteryReportRequest normalize(LotteryReportRequest request) {
        if (request == null) {
            throw new BadRequestException("lottery report request is required");
        }
        if (request.dateFrom() != null && request.dateTo() != null && request.dateTo().isBefore(request.dateFrom())) {
            throw new BadRequestException("dateTo must be on or after dateFrom");
        }
        return request;
    }

    private static List<LotteryPayout> referralPayouts(List<LotteryPayout> payouts, List<LotteryPayoutApproval> approvals) {
        Map<UUID, LotteryPayout> indexed = new LinkedHashMap<>();
        Stream.concat(
                        payouts.stream().filter(payout -> payout.getStatus() == LotteryPayoutStatus.REFERRED_TO_OPERATOR),
                        approvals.stream()
                                .filter(approval -> approval.getApprovalType() == LotteryPayoutApprovalType.OPERATOR_REFERRAL)
                                .map(LotteryPayoutApproval::getPayout))
                .sorted(Comparator.comparing(LotteryPayout::getOccurredAt).reversed())
                .forEach(payout -> indexed.putIfAbsent(payout.getId(), payout));
        return new ArrayList<>(indexed.values());
    }

    private static LotteryReportApprovalRow approvalRow(LotteryPayoutApproval approval) {
        LotteryPayout payout = approval.getPayout();
        return new LotteryReportApprovalRow(
                approval.getId(),
                payout.getId(),
                payout.getTicketNumber(),
                payout.getOperator().getId(),
                payout.getOperator().getCode(),
                payout.getOperator().getName(),
                payout.getStore().getId(),
                payout.getStore().getCode(),
                payout.getStore().getName(),
                payout.getRegister().getId(),
                payout.getRegister().getCode(),
                payout.getRegister().getName(),
                payout.getCashier().getId(),
                payout.getCashier().getEmail(),
                payout.getCashier().getDisplayName(),
                approval.getApprovalType(),
                approval.getApprovedBy().getId(),
                approval.getApprovedBy().getEmail(),
                approval.getApprovedBy().getDisplayName(),
                approval.getApprovedAt(),
                money(approval.getPayoutAmount()),
                money(approval.getThresholdAmount()),
                approval.getNotes());
    }

    private static LotteryReportCommissionRow commissionRow(LotterySettlement settlement) {
        return new LotteryReportCommissionRow(
                settlement.getId(),
                settlement.getOperator().getId(),
                settlement.getOperator().getCode(),
                settlement.getOperator().getName(),
                settlement.getStore().getId(),
                settlement.getStore().getCode(),
                settlement.getStore().getName(),
                settlement.getPeriodStart(),
                settlement.getPeriodEnd(),
                money(settlement.getGrossSales()),
                money(settlement.getTotalPayouts()),
                money(settlement.getCommission()),
                money(settlement.getExpectedSettlement()),
                settlement.getStatus());
    }

    private static List<LotteryReportChartPoint> chartRows(
            List<LotterySale> sales,
            List<LotteryPayout> payouts,
            List<LotteryPayoutReversal> reversals,
            List<LotteryPayout> referrals,
            List<LotterySettlement> settlements) {
        Map<LocalDate, ChartTotals> buckets = new LinkedHashMap<>();
        sales.stream()
                .filter(sale -> SETTLEMENT_SALE_STATUSES.contains(sale.getStatus()))
                .forEach(sale -> bucket(buckets, sale.getOccurredAt()).sales = bucket(buckets, sale.getOccurredAt()).sales.add(sale.getAmount()));
        payouts.stream()
                .filter(payout -> SETTLEMENT_PAYOUT_STATUSES.contains(payout.getStatus()))
                .forEach(payout -> bucket(buckets, payout.getOccurredAt()).payouts = bucket(buckets, payout.getOccurredAt()).payouts.add(payout.getAmount()));
        reversals.forEach(reversal -> bucket(buckets, reversal.getReversedAt()).reversals = bucket(buckets, reversal.getReversedAt()).reversals.add(reversal.getAmount()));
        referrals.forEach(referral -> bucket(buckets, referral.getOccurredAt()).referrals = bucket(buckets, referral.getOccurredAt()).referrals.add(referral.getAmount()));
        settlements.forEach(settlement -> bucket(buckets, settlement.getPeriodEnd()).settlement = bucket(buckets, settlement.getPeriodEnd()).settlement.add(settlement.getExpectedSettlement()));
        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new LotteryReportChartPoint(
                        entry.getKey(),
                        money(entry.getValue().sales),
                        money(entry.getValue().payouts),
                        money(entry.getValue().reversals),
                        money(entry.getValue().referrals),
                        money(entry.getValue().settlement)))
                .toList();
    }

    private static ChartTotals bucket(Map<LocalDate, ChartTotals> buckets, Instant instant) {
        return buckets.computeIfAbsent(instant.atZone(ZoneOffset.UTC).toLocalDate(), ignored -> new ChartTotals());
    }

    private static ChartTotals bucket(Map<LocalDate, ChartTotals> buckets, LocalDate date) {
        return buckets.computeIfAbsent(date, ignored -> new ChartTotals());
    }

    private static Specification<LotterySale> saleSpecification(LotteryReportRequest request) {
        return Specification
                .<LotterySale>where(equalReference("operator", request.operatorId()))
                .and(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("cashier", request.cashierId()))
                .and(instantGreaterThanOrEqualTo("occurredAt", request.dateFrom()))
                .and(instantBeforeDayAfter("occurredAt", request.dateTo()));
    }

    private static Specification<LotteryPayout> payoutSpecification(LotteryReportRequest request) {
        return Specification
                .<LotteryPayout>where(equalReference("operator", request.operatorId()))
                .and(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("cashier", request.cashierId()))
                .and(instantGreaterThanOrEqualTo("occurredAt", request.dateFrom()))
                .and(instantBeforeDayAfter("occurredAt", request.dateTo()));
    }

    private static Specification<LotteryPayoutApproval> approvalSpecification(LotteryReportRequest request) {
        return Specification
                .where(equalPayoutReference("operator", request.operatorId()))
                .and(equalPayoutReference("store", request.storeId()))
                .and(equalPayoutReference("register", request.registerId()))
                .and(equalPayoutReference("cashier", request.cashierId()))
                .and(approvalAtGreaterThanOrEqualTo(request.dateFrom()))
                .and(approvalAtBeforeDayAfter(request.dateTo()));
    }

    private static Specification<LotteryPayoutReversal> reversalSpecification(LotteryReportRequest request) {
        return Specification
                .where(equalOriginalPayoutReference("operator", request.operatorId()))
                .and(equalOriginalPayoutReference("store", request.storeId()))
                .and(equalOriginalPayoutReference("register", request.registerId()))
                .and(equalOriginalPayoutReference("cashier", request.cashierId()))
                .and(reversedAtGreaterThanOrEqualTo(request.dateFrom()))
                .and(reversedAtBeforeDayAfter(request.dateTo()));
    }

    private static Specification<LotterySaleCancellation> cancellationSpecification(LotteryReportRequest request) {
        return Specification
                .where(equalOriginalSaleReference("operator", request.operatorId()))
                .and(equalOriginalSaleReference("store", request.storeId()))
                .and(equalOriginalSaleReference("register", request.registerId()))
                .and(equalOriginalSaleReference("cashier", request.cashierId()))
                .and(cancelledAtGreaterThanOrEqualTo(request.dateFrom()))
                .and(cancelledAtBeforeDayAfter(request.dateTo()));
    }

    private static Specification<LotterySettlement> settlementSpecification(LotteryReportRequest request) {
        return Specification
                .where(equalSettlementReference("operator", request.operatorId()))
                .and(equalSettlementReference("store", request.storeId()))
                .and(noRegisterOrCashierFilter(request.registerId(), request.cashierId()))
                .and(periodEndGreaterThanOrEqualTo(request.dateFrom()))
                .and(periodStartLessThanOrEqualTo(request.dateTo()));
    }

    private static <T> Specification<T> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotteryPayoutApproval> equalPayoutReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("payout").get(field).get("id"), value);
    }

    private static Specification<LotteryPayoutReversal> equalOriginalPayoutReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("originalPayout").get(field).get("id"), value);
    }

    private static Specification<LotterySaleCancellation> equalOriginalSaleReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("originalSale").get(field).get("id"), value);
    }

    private static Specification<LotterySettlement> equalSettlementReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static <T> Specification<T> instantGreaterThanOrEqualTo(String field, LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant start = value.atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get(field), start);
    }

    private static <T> Specification<T> instantBeforeDayAfter(String field, LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant exclusiveEnd = value.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get(field), exclusiveEnd);
    }

    private static Specification<LotteryPayoutApproval> approvalAtGreaterThanOrEqualTo(LocalDate value) {
        return instantGreaterThanOrEqualTo("approvedAt", value);
    }

    private static Specification<LotteryPayoutApproval> approvalAtBeforeDayAfter(LocalDate value) {
        return instantBeforeDayAfter("approvedAt", value);
    }

    private static Specification<LotteryPayoutReversal> reversedAtGreaterThanOrEqualTo(LocalDate value) {
        return instantGreaterThanOrEqualTo("reversedAt", value);
    }

    private static Specification<LotteryPayoutReversal> reversedAtBeforeDayAfter(LocalDate value) {
        return instantBeforeDayAfter("reversedAt", value);
    }

    private static Specification<LotterySaleCancellation> cancelledAtGreaterThanOrEqualTo(LocalDate value) {
        return instantGreaterThanOrEqualTo("cancelledAt", value);
    }

    private static Specification<LotterySaleCancellation> cancelledAtBeforeDayAfter(LocalDate value) {
        return instantBeforeDayAfter("cancelledAt", value);
    }

    private static Specification<LotterySettlement> noRegisterOrCashierFilter(UUID registerId, UUID cashierId) {
        if (registerId == null && cashierId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
    }

    private static Specification<LotterySettlement> periodEndGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("periodEnd"), value);
    }

    private static Specification<LotterySettlement> periodStartLessThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("periodStart"), value);
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

    private static class ChartTotals {
        private BigDecimal sales = moneyZero();
        private BigDecimal payouts = moneyZero();
        private BigDecimal reversals = moneyZero();
        private BigDecimal referrals = moneyZero();
        private BigDecimal settlement = moneyZero();
    }
}
