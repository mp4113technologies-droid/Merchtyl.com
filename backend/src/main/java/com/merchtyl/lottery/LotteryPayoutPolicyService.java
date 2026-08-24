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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class LotteryPayoutPolicyService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate OPEN_ENDED_EFFECTIVE_TO = LocalDate.of(9999, 12, 31);
    private static final Set<LotteryPayoutPolicyStatus> OVERLAP_BLOCKING_STATUSES = Set.of(
            LotteryPayoutPolicyStatus.ACTIVE,
            LotteryPayoutPolicyStatus.SCHEDULED);

    private final LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository;
    private final LotteryOperatorRepository lotteryOperatorRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final StoreRepository storeRepository;
    private final FeatureService featureService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LotteryPayoutPolicyService(
            LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            TaxJurisdictionRepository taxJurisdictionRepository,
            StoreRepository storeRepository,
            FeatureService featureService,
            UserRepository userRepository,
            AuditService auditService) {
        this.lotteryPayoutPolicyRepository = lotteryPayoutPolicyRepository;
        this.lotteryOperatorRepository = lotteryOperatorRepository;
        this.taxJurisdictionRepository = taxJurisdictionRepository;
        this.storeRepository = storeRepository;
        this.featureService = featureService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public LotteryPayoutPolicyResponse create(LotteryPayoutPolicyRequest request, Authentication authentication) {
        requireFeature();
        LotteryPayoutPolicyValues values = values(request);
        requireNoOverlap(values, null);
        LotteryPayoutPolicyResponse response = LotteryPayoutPolicyResponse.from(
                lotteryPayoutPolicyRepository.saveAndFlush(new LotteryPayoutPolicy(values)));
        audit(authentication, AuditAction.LOTTERY_PAYOUT_POLICY_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<LotteryPayoutPolicyResponse> search(LotteryPayoutPolicySearchRequest request) {
        requireFeature();
        int requestedPage = request.page() == null ? 0 : request.page();
        int requestedSize = request.size() == null ? 20 : request.size();
        int pageNumber = Math.max(0, requestedPage);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
        var page = lotteryPayoutPolicyRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by("operator.name")
                                .and(Sort.by(Sort.Direction.DESC, "effectiveFrom"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(LotteryPayoutPolicyResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public LotteryPayoutPolicyResponse get(UUID id) {
        requireFeature();
        return LotteryPayoutPolicyResponse.from(find(id));
    }

    @Transactional
    public LotteryPayoutPolicyResponse update(UUID id, LotteryPayoutPolicyUpdateRequest request, Authentication authentication) {
        requireFeature();
        LotteryPayoutPolicy policy = find(id);
        requireCurrentVersion(policy, request.version());
        LotteryPayoutPolicyValues values = values(request);
        requireNoOverlap(values, id);
        LotteryPayoutPolicyResponse before = LotteryPayoutPolicyResponse.from(policy);
        policy.update(values);
        LotteryPayoutPolicyResponse after = LotteryPayoutPolicyResponse.from(lotteryPayoutPolicyRepository.saveAndFlush(policy));
        audit(authentication, AuditAction.LOTTERY_PAYOUT_POLICY_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public LotteryPayoutPolicyResponse updateStatus(UUID id, LotteryPayoutPolicyStatusRequest request, Authentication authentication) {
        requireFeature();
        LotteryPayoutPolicy policy = find(id);
        requireCurrentVersion(policy, request.version());
        requireStatus(request.status());
        if (OVERLAP_BLOCKING_STATUSES.contains(request.status())) {
            requireNoOverlap(new LotteryPayoutPolicyValues(
                    policy.getOperator(),
                    policy.getJurisdiction(),
                    policy.getStore(),
                    policy.getMaximumCashPayout(),
                    policy.getCashierApprovalLimit(),
                    policy.getManagerApprovalThreshold(),
                    policy.getOperatorReferralThreshold(),
                    policy.getProtectedRegisterFloat(),
                    policy.isAllowCashPayout(),
                    policy.isAllowStoreCredit(),
                    policy.isRequireTicketValidation(),
                    policy.isRequireAgeVerification(),
                    policy.isRequireCustomerIdentification(),
                    policy.isAllowAlternateRegister(),
                    policy.getEffectiveFrom(),
                    policy.getEffectiveTo(),
                    request.status()), id);
        }
        LotteryPayoutPolicyResponse before = LotteryPayoutPolicyResponse.from(policy);
        policy.setStatus(request.status());
        LotteryPayoutPolicyResponse after = LotteryPayoutPolicyResponse.from(lotteryPayoutPolicyRepository.saveAndFlush(policy));
        audit(authentication, AuditAction.LOTTERY_PAYOUT_POLICY_STATUS_CHANGED, id, before, after);
        return after;
    }

    private LotteryPayoutPolicyValues values(LotteryPayoutPolicyRequest request) {
        return values(
                request.operatorId(),
                request.jurisdictionId(),
                request.storeId(),
                request.maximumCashPayout(),
                request.cashierApprovalLimit(),
                request.managerApprovalThreshold(),
                request.operatorReferralThreshold(),
                request.protectedRegisterFloat(),
                request.allowCashPayout(),
                request.allowStoreCredit(),
                request.requireTicketValidation(),
                request.requireAgeVerification(),
                request.requireCustomerIdentification(),
                request.allowAlternateRegister(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status());
    }

    private LotteryPayoutPolicyValues values(LotteryPayoutPolicyUpdateRequest request) {
        return values(
                request.operatorId(),
                request.jurisdictionId(),
                request.storeId(),
                request.maximumCashPayout(),
                request.cashierApprovalLimit(),
                request.managerApprovalThreshold(),
                request.operatorReferralThreshold(),
                request.protectedRegisterFloat(),
                request.allowCashPayout(),
                request.allowStoreCredit(),
                request.requireTicketValidation(),
                request.requireAgeVerification(),
                request.requireCustomerIdentification(),
                request.allowAlternateRegister(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status());
    }

    private LotteryPayoutPolicyValues values(
            UUID operatorId,
            UUID jurisdictionId,
            UUID storeId,
            BigDecimal maximumCashPayout,
            BigDecimal cashierApprovalLimit,
            BigDecimal managerApprovalThreshold,
            BigDecimal operatorReferralThreshold,
            BigDecimal protectedRegisterFloat,
            boolean allowCashPayout,
            boolean allowStoreCredit,
            boolean requireTicketValidation,
            boolean requireAgeVerification,
            boolean requireCustomerIdentification,
            boolean allowAlternateRegister,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            LotteryPayoutPolicyStatus status) {
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
        return new LotteryPayoutPolicyValues(
                operator,
                jurisdiction,
                store(storeId),
                nonNegative(maximumCashPayout, "maximumCashPayout"),
                nonNegative(cashierApprovalLimit, "cashierApprovalLimit"),
                nonNegative(managerApprovalThreshold, "managerApprovalThreshold"),
                nonNegative(operatorReferralThreshold, "operatorReferralThreshold"),
                nonNegative(protectedRegisterFloat, "protectedRegisterFloat"),
                allowCashPayout,
                allowStoreCredit,
                requireTicketValidation,
                requireAgeVerification,
                requireCustomerIdentification,
                allowAlternateRegister,
                effectiveFrom,
                effectiveTo,
                requireStatus(status));
    }

    private void requireNoOverlap(LotteryPayoutPolicyValues values, UUID excludeId) {
        if (!OVERLAP_BLOCKING_STATUSES.contains(values.status())) {
            return;
        }
        LocalDate effectiveTo = values.effectiveTo() == null ? OPEN_ENDED_EFFECTIVE_TO : values.effectiveTo();
        if (lotteryPayoutPolicyRepository.existsOverlappingPolicy(
                values.operator().getId(),
                values.jurisdiction().getId(),
                values.store().getId(),
                values.effectiveFrom(),
                effectiveTo,
                OVERLAP_BLOCKING_STATUSES,
                excludeId)) {
            throw new ConflictException("Lottery payout policy effective period overlaps an active or scheduled policy for this operator, jurisdiction, and store");
        }
    }

    private LotteryPayoutPolicy find(UUID id) {
        return lotteryPayoutPolicyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery payout policy not found"));
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

    private void requireCurrentVersion(LotteryPayoutPolicy policy, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != policy.getVersion()) {
            throw new ConflictException("Lottery payout policy was modified by another transaction");
        }
    }

    private void requireFeature() {
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, null, null);
    }

    private Specification<LotteryPayoutPolicy> specification(LotteryPayoutPolicySearchRequest request) {
        return Specification
                .where(equalReference("operator", request.operatorId()))
                .and(equalReference("jurisdiction", request.jurisdictionId()))
                .and(equalReference("store", request.storeId()))
                .and(equalEnum("status", request.status()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "LOTTERY_PAYOUT_POLICY",
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

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new BadRequestException(field + " must be zero or greater");
        }
        return value;
    }

    private static LotteryPayoutPolicyStatus requireStatus(LotteryPayoutPolicyStatus status) {
        if (status == null) {
            throw new BadRequestException("status is required");
        }
        return status;
    }

    private static Specification<LotteryPayoutPolicy> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotteryPayoutPolicy> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }
}
