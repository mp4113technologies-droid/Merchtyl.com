package com.merchtyl.lottery;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.tax.TaxJurisdiction;
import com.merchtyl.tax.TaxJurisdictionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LotteryCommissionRuleService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate OPEN_ENDED_EFFECTIVE_TO = LocalDate.of(9999, 12, 31);
    private static final Set<LotteryCommissionRuleStatus> OVERLAP_BLOCKING_STATUSES = Set.of(LotteryCommissionRuleStatus.ACTIVE);

    private final LotteryCommissionRuleRepository lotteryCommissionRuleRepository;
    private final LotteryOperatorRepository lotteryOperatorRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final StoreRepository storeRepository;
    private final FeatureService featureService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LotteryCommissionRuleService(
            LotteryCommissionRuleRepository lotteryCommissionRuleRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            TaxJurisdictionRepository taxJurisdictionRepository,
            StoreRepository storeRepository,
            FeatureService featureService,
            UserRepository userRepository,
            AuditService auditService) {
        this.lotteryCommissionRuleRepository = lotteryCommissionRuleRepository;
        this.lotteryOperatorRepository = lotteryOperatorRepository;
        this.taxJurisdictionRepository = taxJurisdictionRepository;
        this.storeRepository = storeRepository;
        this.featureService = featureService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public LotteryCommissionRuleResponse create(LotteryCommissionRuleRequest request, Authentication authentication) {
        requireFeature();
        LotteryCommissionRuleValues values = values(request);
        requireNoOverlap(values, null);
        LotteryCommissionRuleResponse response = LotteryCommissionRuleResponse.from(
                lotteryCommissionRuleRepository.saveAndFlush(new LotteryCommissionRule(values)));
        audit(authentication, AuditAction.LOTTERY_COMMISSION_RULE_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<LotteryCommissionRuleResponse> search(LotteryCommissionRuleSearchRequest request) {
        requireFeature();
        int requestedPage = request.page() == null ? 0 : request.page();
        int requestedSize = request.size() == null ? 20 : request.size();
        int pageNumber = Math.max(0, requestedPage);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
        var page = lotteryCommissionRuleRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by("operator.name")
                                .and(Sort.by(Sort.Direction.DESC, "effectiveFrom"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(LotteryCommissionRuleResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public LotteryCommissionRuleResponse get(UUID id) {
        requireFeature();
        return LotteryCommissionRuleResponse.from(find(id));
    }

    @Transactional
    public LotteryCommissionRuleResponse update(UUID id, LotteryCommissionRuleUpdateRequest request, Authentication authentication) {
        requireFeature();
        LotteryCommissionRule rule = find(id);
        requireCurrentVersion(rule, request.version());
        LotteryCommissionRuleValues values = values(request);
        requireNoOverlap(values, id);
        LotteryCommissionRuleResponse before = LotteryCommissionRuleResponse.from(rule);
        rule.update(values);
        LotteryCommissionRuleResponse after = LotteryCommissionRuleResponse.from(lotteryCommissionRuleRepository.saveAndFlush(rule));
        audit(authentication, AuditAction.LOTTERY_COMMISSION_RULE_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public void delete(UUID id, Long version, Authentication authentication) {
        requireFeature();
        LotteryCommissionRule rule = find(id);
        requireCurrentVersion(rule, version);
        LotteryCommissionRuleResponse before = LotteryCommissionRuleResponse.from(rule);
        lotteryCommissionRuleRepository.delete(rule);
        audit(authentication, AuditAction.LOTTERY_COMMISSION_RULE_DELETED, id, before, null);
    }

    private LotteryCommissionRuleValues values(LotteryCommissionRuleRequest request) {
        return values(
                request.name(),
                request.operatorId(),
                request.jurisdictionId(),
                request.storeId(),
                request.ruleType(),
                request.commissionRatePercent(),
                request.fixedAmount(),
                request.currencyCode(),
                request.fixedPeriod(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status(),
                request.notes());
    }

    private LotteryCommissionRuleValues values(LotteryCommissionRuleUpdateRequest request) {
        return values(
                request.name(),
                request.operatorId(),
                request.jurisdictionId(),
                request.storeId(),
                request.ruleType(),
                request.commissionRatePercent(),
                request.fixedAmount(),
                request.currencyCode(),
                request.fixedPeriod(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status(),
                request.notes());
    }

    private LotteryCommissionRuleValues values(
            String name,
            UUID operatorId,
            UUID jurisdictionId,
            UUID storeId,
            LotteryCommissionRuleType ruleType,
            BigDecimal commissionRatePercent,
            BigDecimal fixedAmount,
            String currencyCode,
            LotteryCommissionPeriod fixedPeriod,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            LotteryCommissionRuleStatus status,
            String notes) {
        LotteryOperator operator = operator(operatorId);
        TaxJurisdiction jurisdiction = jurisdiction(jurisdictionId);
        if (!operator.getJurisdiction().getId().equals(jurisdiction.getId())) {
            throw new BadRequestException("jurisdictionId must match the lottery operator jurisdiction");
        }
        if (effectiveFrom == null) {
            throw new BadRequestException("effectiveFrom is required");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BadRequestException("effectiveTo must be on or after effectiveFrom");
        }
        LotteryCommissionRuleType requiredRuleType = requireNonNull(ruleType, "ruleType");
        RuleAmounts amounts = validateRuleAmounts(requiredRuleType, commissionRatePercent, fixedAmount, currencyCode, fixedPeriod);
        return new LotteryCommissionRuleValues(
                cleanRequired(name, "name", 120),
                operator,
                jurisdiction,
                store(storeId),
                requiredRuleType,
                amounts.commissionRatePercent(),
                amounts.fixedAmount(),
                amounts.currencyCode(),
                amounts.fixedPeriod(),
                effectiveFrom,
                effectiveTo,
                requireNonNull(status, "status"),
                cleanOptional(notes, 500));
    }

    private void requireNoOverlap(LotteryCommissionRuleValues values, UUID excludeId) {
        if (!OVERLAP_BLOCKING_STATUSES.contains(values.status())) {
            return;
        }
        LocalDate effectiveTo = values.effectiveTo() == null ? OPEN_ENDED_EFFECTIVE_TO : values.effectiveTo();
        if (lotteryCommissionRuleRepository.existsOverlappingRule(
                values.operator().getId(),
                values.jurisdiction().getId(),
                values.store().getId(),
                values.ruleType(),
                values.effectiveFrom(),
                effectiveTo,
                OVERLAP_BLOCKING_STATUSES,
                excludeId)) {
            throw new ConflictException("Lottery commission rule effective period overlaps an active rule for this operator, jurisdiction, store, and type");
        }
    }

    private LotteryCommissionRule find(UUID id) {
        return lotteryCommissionRuleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery commission rule not found"));
    }

    private LotteryOperator operator(UUID id) {
        if (id == null) {
            throw new BadRequestException("operatorId is required");
        }
        return lotteryOperatorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery operator not found"));
    }

    private TaxJurisdiction jurisdiction(UUID id) {
        if (id == null) {
            throw new BadRequestException("jurisdictionId is required");
        }
        return taxJurisdictionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tax jurisdiction not found"));
    }

    private Store store(UUID id) {
        if (id == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private void requireCurrentVersion(LotteryCommissionRule rule, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != rule.getVersion()) {
            throw new ConflictException("Lottery commission rule was modified by another transaction");
        }
    }

    private void requireFeature() {
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, null, null);
    }

    private Specification<LotteryCommissionRule> specification(LotteryCommissionRuleSearchRequest request) {
        return Specification
                .where(equalReference("operator", request.operatorId()))
                .and(equalReference("jurisdiction", request.jurisdictionId()))
                .and(equalReference("store", request.storeId()))
                .and(equalEnum("ruleType", request.ruleType()))
                .and(equalEnum("status", request.status()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "LOTTERY_COMMISSION_RULE",
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

    private static RuleAmounts validateRuleAmounts(
            LotteryCommissionRuleType ruleType,
            BigDecimal commissionRatePercent,
            BigDecimal fixedAmount,
            String currencyCode,
            LotteryCommissionPeriod fixedPeriod) {
        return switch (ruleType) {
            case PERCENT_OF_SALES, PERCENT_OF_PAYOUT -> new RuleAmounts(
                    positivePercent(commissionRatePercent, "commissionRatePercent"),
                    null,
                    null,
                    null);
            case FIXED_PER_TRANSACTION -> new RuleAmounts(
                    null,
                    positiveMoney(fixedAmount, "fixedAmount"),
                    normalizeCurrencyCode(currencyCode),
                    null);
            case FIXED_PER_PERIOD -> new RuleAmounts(
                    null,
                    positiveMoney(fixedAmount, "fixedAmount"),
                    normalizeCurrencyCode(currencyCode),
                    requireNonNull(fixedPeriod, "fixedPeriod"));
            case MANUAL -> new RuleAmounts(null, null, null, null);
        };
    }

    private static BigDecimal positivePercent(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("100.0000")) > 0) {
            throw new BadRequestException(field + " must be greater than 0 and no more than 100");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveMoney(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new BadRequestException(field + " must be greater than 0.00");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(field + " may include no more than 2 decimal places");
        }
    }

    private static String normalizeCurrencyCode(String value) {
        String currencyCode = cleanRequired(value, "currencyCode", 3).toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("currencyCode must be a valid ISO 4217 code");
        }
        return currencyCode;
    }

    private static String cleanRequired(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BadRequestException(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return trimmed;
    }

    private static String cleanOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BadRequestException("notes must be " + maxLength + " characters or fewer");
        }
        return trimmed;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private static Specification<LotteryCommissionRule> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotteryCommissionRule> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private record RuleAmounts(
            BigDecimal commissionRatePercent,
            BigDecimal fixedAmount,
            String currencyCode,
            LotteryCommissionPeriod fixedPeriod) {
    }
}
