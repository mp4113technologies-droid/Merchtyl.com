package com.merchtyl.lottery;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.tax.TaxJurisdiction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Service
public class LotterySettlementService {
    private static final int MONEY_SCALE = 2;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<LotterySaleStatus> SALE_STATUSES = Set.of(
            LotterySaleStatus.RECORDED,
            LotterySaleStatus.CANCELLED);
    private static final Set<LotteryPayoutStatus> PAYOUT_STATUSES = Set.of(
            LotteryPayoutStatus.PAID,
            LotteryPayoutStatus.REVERSED);

    private final LotterySettlementRepository lotterySettlementRepository;
    private final LotteryOperatorRepository lotteryOperatorRepository;
    private final StoreRepository storeRepository;
    private final LotterySaleRepository lotterySaleRepository;
    private final LotteryPayoutRepository lotteryPayoutRepository;
    private final LotterySaleCancellationRepository lotterySaleCancellationRepository;
    private final LotteryPayoutReversalRepository lotteryPayoutReversalRepository;
    private final LotteryCommissionRuleRepository lotteryCommissionRuleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public LotterySettlementService(
            LotterySettlementRepository lotterySettlementRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            StoreRepository storeRepository,
            LotterySaleRepository lotterySaleRepository,
            LotteryPayoutRepository lotteryPayoutRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotteryCommissionRuleRepository lotteryCommissionRuleRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this(
                lotterySettlementRepository,
                lotteryOperatorRepository,
                storeRepository,
                lotterySaleRepository,
                lotteryPayoutRepository,
                lotterySaleCancellationRepository,
                lotteryPayoutReversalRepository,
                lotteryCommissionRuleRepository,
                userRepository,
                auditService,
                Clock.systemUTC());
    }

    LotterySettlementService(
            LotterySettlementRepository lotterySettlementRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            StoreRepository storeRepository,
            LotterySaleRepository lotterySaleRepository,
            LotteryPayoutRepository lotteryPayoutRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotteryCommissionRuleRepository lotteryCommissionRuleRepository,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock) {
        this.lotterySettlementRepository = lotterySettlementRepository;
        this.lotteryOperatorRepository = lotteryOperatorRepository;
        this.storeRepository = storeRepository;
        this.lotterySaleRepository = lotterySaleRepository;
        this.lotteryPayoutRepository = lotteryPayoutRepository;
        this.lotterySaleCancellationRepository = lotterySaleCancellationRepository;
        this.lotteryPayoutReversalRepository = lotteryPayoutReversalRepository;
        this.lotteryCommissionRuleRepository = lotteryCommissionRuleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public LotterySettlementResponse calculate(LotterySettlementCalculationRequest request) {
        return calculate(request, null);
    }

    @Transactional
    public LotterySettlementResponse calculate(LotterySettlementCalculationRequest request, Authentication authentication) {
        SettlementContext context = context(request);
        BigDecimal grossSales = money(lotterySaleRepository.sumSettlementSales(
                context.operator().getId(),
                context.store().getId(),
                SALE_STATUSES,
                context.periodStartInstant(),
                context.periodEndExclusiveInstant()));
        long saleCount = lotterySaleRepository.countSettlementSales(
                context.operator().getId(),
                context.store().getId(),
                SALE_STATUSES,
                context.periodStartInstant(),
                context.periodEndExclusiveInstant());
        BigDecimal totalPayouts = money(lotteryPayoutRepository.sumSettlementPayouts(
                context.operator().getId(),
                context.store().getId(),
                PAYOUT_STATUSES,
                context.periodStart(),
                context.periodEnd()));
        long payoutCount = lotteryPayoutRepository.countSettlementPayouts(
                context.operator().getId(),
                context.store().getId(),
                PAYOUT_STATUSES,
                context.periodStart(),
                context.periodEnd());
        BigDecimal cancellations = money(lotterySaleCancellationRepository.sumSettlementCancellations(
                context.operator().getId(),
                context.store().getId(),
                context.periodStartInstant(),
                context.periodEndExclusiveInstant()));
        long cancellationCount = lotterySaleCancellationRepository.countSettlementCancellations(
                context.operator().getId(),
                context.store().getId(),
                context.periodStartInstant(),
                context.periodEndExclusiveInstant());
        BigDecimal adjustments = money(lotteryPayoutReversalRepository.sumSettlementAdjustments(
                context.operator().getId(),
                context.store().getId(),
                context.periodStartInstant(),
                context.periodEndExclusiveInstant()));
        long adjustmentCount = lotteryPayoutReversalRepository.countSettlementAdjustments(
                context.operator().getId(),
                context.store().getId(),
                context.periodStartInstant(),
                context.periodEndExclusiveInstant());
        BigDecimal commission = commission(
                context,
                grossSales,
                totalPayouts,
                saleCount + payoutCount + cancellationCount + adjustmentCount);
        BigDecimal expectedSettlement = grossSales
                .subtract(totalPayouts)
                .subtract(cancellations)
                .add(adjustments)
                .subtract(commission)
                .setScale(MONEY_SCALE);
        LotterySettlementValues values = new LotterySettlementValues(
                context.operator(),
                context.operator().getJurisdiction(),
                context.store(),
                context.periodStart(),
                context.periodEnd(),
                grossSales,
                totalPayouts,
                cancellations,
                adjustments,
                commission,
                expectedSettlement,
                context.store().getCurrencyCode(),
                Instant.now(clock));
        LotterySettlement settlement = lotterySettlementRepository
                .findByOperator_IdAndStore_IdAndPeriodStartAndPeriodEnd(
                        context.operator().getId(),
                        context.store().getId(),
                        context.periodStart(),
                        context.periodEnd())
                .map(existing -> {
                    if (existing.getStatus() == LotterySettlementStatus.APPROVED || existing.getStatus() == LotterySettlementStatus.POSTED) {
                        throw new ConflictException("Approved or posted settlement must be reopened before recalculation");
                    }
                    existing.update(values);
                    return existing;
                })
                .orElseGet(() -> new LotterySettlement(values));
        LotterySettlementResponse response = LotterySettlementResponse.from(lotterySettlementRepository.saveAndFlush(settlement));
        audit(authentication, AuditAction.LOTTERY_SETTLEMENT_CALCULATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<LotterySettlementResponse> search(LotterySettlementSearchRequest request) {
        int requestedPage = request.page() == null ? 0 : request.page();
        int requestedSize = request.size() == null ? 20 : request.size();
        int pageNumber = Math.max(0, requestedPage);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
        var page = lotterySettlementRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "periodEnd")
                                .and(Sort.by("operator.name"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(LotterySettlementResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public LotterySettlementResponse get(UUID id) {
        return LotterySettlementResponse.from(find(id));
    }

    @Transactional
    public LotterySettlementResponse approve(UUID id, LotterySettlementLifecycleRequest request, Authentication authentication) {
        LotterySettlement settlement = find(id);
        requireCurrentVersion(settlement, request.version());
        if (settlement.getStatus() != LotterySettlementStatus.CALCULATED
                && settlement.getStatus() != LotterySettlementStatus.UNDER_REVIEW
                && settlement.getStatus() != LotterySettlementStatus.REOPENED) {
            throw new ConflictException("Settlement must be calculated, under review, or reopened before approval");
        }
        LotterySettlementResponse before = LotterySettlementResponse.from(settlement);
        settlement.approve(actor(authentication), Instant.now(clock), cleanOptional(request.notes()));
        LotterySettlementResponse after = LotterySettlementResponse.from(lotterySettlementRepository.saveAndFlush(settlement));
        audit(authentication, AuditAction.LOTTERY_SETTLEMENT_APPROVED, id, before, after);
        return after;
    }

    @Transactional
    public LotterySettlementResponse reopen(UUID id, LotterySettlementLifecycleRequest request, Authentication authentication) {
        LotterySettlement settlement = find(id);
        requireCurrentVersion(settlement, request.version());
        if (settlement.getStatus() != LotterySettlementStatus.APPROVED && settlement.getStatus() != LotterySettlementStatus.POSTED) {
            throw new ConflictException("Settlement must be approved or posted before reopening");
        }
        LotterySettlementResponse before = LotterySettlementResponse.from(settlement);
        settlement.reopen(actor(authentication), Instant.now(clock), cleanRequired(request.reason(), "reason"));
        LotterySettlementResponse after = LotterySettlementResponse.from(lotterySettlementRepository.saveAndFlush(settlement));
        audit(authentication, AuditAction.LOTTERY_SETTLEMENT_REOPENED, id, before, after);
        return after;
    }

    @Transactional
    public LotterySettlementResponse post(UUID id, LotterySettlementLifecycleRequest request, Authentication authentication) {
        LotterySettlement settlement = find(id);
        requireCurrentVersion(settlement, request.version());
        if (settlement.getStatus() != LotterySettlementStatus.APPROVED) {
            throw new ConflictException("Settlement must be approved before posting");
        }
        LotterySettlementResponse before = LotterySettlementResponse.from(settlement);
        settlement.post(actor(authentication), Instant.now(clock), cleanOptional(request.notes()));
        LotterySettlementResponse after = LotterySettlementResponse.from(lotterySettlementRepository.saveAndFlush(settlement));
        audit(authentication, AuditAction.LOTTERY_SETTLEMENT_POSTED, id, before, after);
        return after;
    }

    private SettlementContext context(LotterySettlementCalculationRequest request) {
        if (request == null) {
            throw new BadRequestException("settlement calculation request is required");
        }
        if (request.operatorId() == null) {
            throw new BadRequestException("operatorId is required");
        }
        if (request.storeId() == null) {
            throw new BadRequestException("storeId is required");
        }
        if (request.periodStart() == null) {
            throw new BadRequestException("periodStart is required");
        }
        if (request.periodEnd() == null) {
            throw new BadRequestException("periodEnd is required");
        }
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new BadRequestException("periodEnd must be on or after periodStart");
        }
        LotteryOperator operator = lotteryOperatorRepository.findById(request.operatorId())
                .orElseThrow(() -> new NotFoundException("Lottery operator not found"));
        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new NotFoundException("Store not found"));
        ZoneId zoneId = ZoneId.of(store.getTimezone());
        Instant start = request.periodStart().atStartOfDay(zoneId).toInstant();
        Instant endExclusive = request.periodEnd().plusDays(1).atStartOfDay(zoneId).toInstant();
        return new SettlementContext(operator, store, request.periodStart(), request.periodEnd(), start, endExclusive);
    }

    private LotterySettlement find(UUID id) {
        return lotterySettlementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery settlement not found"));
    }

    private User actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BadRequestException("authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void requireCurrentVersion(LotterySettlement settlement, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != settlement.getVersion()) {
            throw new ConflictException("Lottery settlement was modified by another transaction");
        }
    }

    private Specification<LotterySettlement> specification(LotterySettlementSearchRequest request) {
        return Specification
                .where(equalReference("operator", request.operatorId()))
                .and(equalReference("store", request.storeId()))
                .and(equalEnum("status", request.status()))
                .and(periodEndGreaterThanOrEqualTo(request.periodStart()))
                .and(periodStartLessThanOrEqualTo(request.periodEnd()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "LOTTERY_SETTLEMENT",
                entityId,
                null,
                null,
                before,
                after,
                null));
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private BigDecimal commission(
            SettlementContext context,
            BigDecimal grossSales,
            BigDecimal totalPayouts,
            long transactionCount) {
        TaxJurisdiction jurisdiction = context.operator().getJurisdiction();
        return lotteryCommissionRuleRepository.findEffectiveSettlementRules(
                        context.operator().getId(),
                        jurisdiction.getId(),
                        context.store().getId(),
                        context.periodStart(),
                        context.periodEnd())
                .stream()
                .map(rule -> commissionFor(rule, grossSales, totalPayouts, transactionCount))
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE);
    }

    private static BigDecimal commissionFor(
            LotteryCommissionRule rule,
            BigDecimal grossSales,
            BigDecimal totalPayouts,
            long transactionCount) {
        return switch (rule.getRuleType()) {
            case PERCENT_OF_SALES -> percent(grossSales, rule.getCommissionRatePercent());
            case PERCENT_OF_PAYOUT -> percent(totalPayouts, rule.getCommissionRatePercent());
            case FIXED_PER_TRANSACTION -> rule.getFixedAmount()
                    .multiply(BigDecimal.valueOf(transactionCount))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            case FIXED_PER_PERIOD -> rule.getFixedAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            case MANUAL -> BigDecimal.ZERO.setScale(MONEY_SCALE);
        };
    }

    private static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate)
                .divide(new BigDecimal("100.0000"), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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

    private static Specification<LotterySettlement> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotterySettlement> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
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

    private record SettlementContext(
            LotteryOperator operator,
            Store store,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant periodStartInstant,
            Instant periodEndExclusiveInstant) {
    }
}
