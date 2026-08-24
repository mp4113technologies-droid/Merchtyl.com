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
import com.merchtyl.tax.TaxJurisdiction;
import com.merchtyl.tax.TaxJurisdictionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class LotteryOperatorService {
    private static final int MAX_PAGE_SIZE = 100;

    private final LotteryOperatorRepository lotteryOperatorRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final FeatureService featureService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LotteryOperatorService(
            LotteryOperatorRepository lotteryOperatorRepository,
            TaxJurisdictionRepository taxJurisdictionRepository,
            FeatureService featureService,
            UserRepository userRepository,
            AuditService auditService) {
        this.lotteryOperatorRepository = lotteryOperatorRepository;
        this.taxJurisdictionRepository = taxJurisdictionRepository;
        this.featureService = featureService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public LotteryOperatorResponse create(LotteryOperatorRequest request, Authentication authentication) {
        requireFeature();
        LotteryOperatorValues values = values(request);
        if (lotteryOperatorRepository.existsByCodeIgnoreCase(values.code())) {
            throw duplicateCode();
        }
        LotteryOperatorResponse response = LotteryOperatorResponse.from(save(new LotteryOperator(values)));
        audit(authentication, AuditAction.LOTTERY_OPERATOR_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<LotteryOperatorResponse> search(LotteryOperatorSearchRequest request) {
        requireFeature();
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = lotteryOperatorRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(LotteryOperatorResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public LotteryOperatorResponse get(UUID id) {
        requireFeature();
        return LotteryOperatorResponse.from(find(id));
    }

    @Transactional
    public LotteryOperatorResponse update(UUID id, LotteryOperatorUpdateRequest request, Authentication authentication) {
        requireFeature();
        LotteryOperator operator = find(id);
        requireCurrentVersion(operator, request.version());
        LotteryOperatorValues values = values(request);
        if (lotteryOperatorRepository.existsByCodeIgnoreCaseAndIdNot(values.code(), id)) {
            throw duplicateCode();
        }
        LotteryOperatorResponse before = LotteryOperatorResponse.from(operator);
        operator.update(values);
        LotteryOperatorResponse after = LotteryOperatorResponse.from(save(operator));
        audit(authentication, AuditAction.LOTTERY_OPERATOR_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public LotteryOperatorResponse updateStatus(UUID id, LotteryOperatorStatusRequest request, Authentication authentication) {
        requireFeature();
        LotteryOperator operator = find(id);
        requireCurrentVersion(operator, request.version());
        LotteryOperatorResponse before = LotteryOperatorResponse.from(operator);
        operator.setActive(request.active());
        LotteryOperatorResponse after = LotteryOperatorResponse.from(save(operator));
        audit(authentication, AuditAction.LOTTERY_OPERATOR_STATUS_CHANGED, id, before, after);
        return after;
    }

    private LotteryOperator save(LotteryOperator operator) {
        try {
            return lotteryOperatorRepository.saveAndFlush(operator);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    private LotteryOperator find(UUID id) {
        return lotteryOperatorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery operator not found"));
    }

    private LotteryOperatorValues values(LotteryOperatorRequest request) {
        return new LotteryOperatorValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                jurisdiction(request.jurisdictionId()),
                optionalText(request.supportContact()),
                requireSettlementFrequency(request.settlementFrequency()),
                request.active());
    }

    private LotteryOperatorValues values(LotteryOperatorUpdateRequest request) {
        return new LotteryOperatorValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                jurisdiction(request.jurisdictionId()),
                optionalText(request.supportContact()),
                requireSettlementFrequency(request.settlementFrequency()),
                request.active());
    }

    private TaxJurisdiction jurisdiction(UUID id) {
        if (id == null) {
            throw new BadRequestException("jurisdictionId is required");
        }
        return taxJurisdictionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tax jurisdiction not found"));
    }

    private SettlementFrequency requireSettlementFrequency(SettlementFrequency value) {
        if (value == null) {
            throw new BadRequestException("settlementFrequency is required");
        }
        return value;
    }

    private Specification<LotteryOperator> specification(LotteryOperatorSearchRequest request) {
        return Specification
                .where(equalString("code", normalizeCodeFilter(request.code())))
                .and(containsString("name", request.name()))
                .and(equalUuid("jurisdiction", request.jurisdictionId()))
                .and(equalEnum("settlementFrequency", request.settlementFrequency()))
                .and(equalBoolean("active", request.active()));
    }

    private void requireCurrentVersion(LotteryOperator operator, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != operator.getVersion()) {
            throw new ConflictException("Lottery operator was modified by another transaction");
        }
    }

    private void requireFeature() {
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, null, null);
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "LOTTERY_OPERATOR",
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

    private static Specification<LotteryOperator> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<LotteryOperator> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    private static Specification<LotteryOperator> equalUuid(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotteryOperator> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<LotteryOperator> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static String normalizeCode(String code) {
        String cleaned = cleanRequired(code, "code").toUpperCase(Locale.ROOT);
        if (!cleaned.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new BadRequestException("code must use letters, numbers, underscores, and hyphens");
        }
        return cleaned;
    }

    private static String normalizeCodeFilter(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private static ConflictException duplicateCode() {
        return new ConflictException("Lottery operator code already exists");
    }
}
