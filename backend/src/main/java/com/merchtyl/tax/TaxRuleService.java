package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaxRuleService {
    private final TaxRuleRepository taxRuleRepository;
    private final TaxGroupService taxGroupService;
    private final TaxComponentService taxComponentService;
    private final TaxCategoryService taxCategoryService;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final ProductRepository productRepository;
    private final TaxRuleEvaluator taxRuleEvaluator;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxRuleService(
            TaxRuleRepository taxRuleRepository,
            TaxGroupService taxGroupService,
            TaxComponentService taxComponentService,
            TaxCategoryService taxCategoryService,
            TaxJurisdictionRepository taxJurisdictionRepository,
            ProductRepository productRepository,
            TaxRuleEvaluator taxRuleEvaluator,
            UserRepository userRepository,
            AuditService auditService) {
        this.taxRuleRepository = taxRuleRepository;
        this.taxGroupService = taxGroupService;
        this.taxComponentService = taxComponentService;
        this.taxCategoryService = taxCategoryService;
        this.taxJurisdictionRepository = taxJurisdictionRepository;
        this.productRepository = productRepository;
        this.taxRuleEvaluator = taxRuleEvaluator;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxRuleResponse create(TaxRuleRequest request, Authentication authentication) {
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxRuleRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        TaxRuleResponse response = TaxRuleResponse.from(save(new TaxRule(values(
                code,
                request.name(),
                request.description(),
                request.priority(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.active(),
                request.conditions(),
                request.actions()))));
        audit(authentication, AuditAction.TAX_RULE_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxRuleResponse> search(TaxRuleSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxRuleRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by("priority").and(Sort.by("code")).and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(TaxRuleResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxRuleResponse get(UUID id) {
        return TaxRuleResponse.from(find(id));
    }

    @Transactional
    public TaxRuleResponse update(UUID id, TaxRuleUpdateRequest request, Authentication authentication) {
        TaxRule rule = find(id);
        TaxGeographySupport.requireCurrentVersion(rule.getVersion(), request.version(), "Tax rule");
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxRuleRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicate();
        }
        TaxRuleResponse before = TaxRuleResponse.from(rule);
        rule.update(values(
                code,
                request.name(),
                request.description(),
                request.priority(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.active(),
                request.conditions(),
                request.actions()));
        TaxRuleResponse after = TaxRuleResponse.from(save(rule));
        audit(authentication, AuditAction.TAX_RULE_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxRuleResponse updateStatus(UUID id, TaxRuleStatusRequest request, Authentication authentication) {
        TaxRule rule = find(id);
        TaxGeographySupport.requireCurrentVersion(rule.getVersion(), request.version(), "Tax rule");
        TaxRuleResponse before = TaxRuleResponse.from(rule);
        rule.setActive(request.active());
        TaxRuleResponse after = TaxRuleResponse.from(save(rule));
        audit(authentication, AuditAction.TAX_RULE_STATUS_CHANGED, id, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public TaxRuleEvaluationResponse evaluate(TaxRuleEvaluationRequest request, Authentication authentication) {
        TaxRuleEvaluationResponse response = taxRuleEvaluator.evaluate(request);
        TaxGeographySupport.audit(authentication, userRepository, auditService, AuditAction.TAX_RULE_EVALUATED, "TAX_RULE_EVALUATION", null, request, response);
        return response;
    }

    TaxRule find(UUID id) {
        return taxRuleRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax rule not found"));
    }

    private TaxRuleMatchResponse evaluateRule(TaxRule rule, TaxRuleEvaluationRequest request) {
        List<TaxRuleConditionEvaluationResponse> conditions = rule.getConditions().stream()
                .map(condition -> evaluateCondition(condition, request))
                .toList();
        boolean matched = conditions.stream().allMatch(TaxRuleConditionEvaluationResponse::matched);
        return new TaxRuleMatchResponse(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getPriority(),
                matched,
                conditions,
                matched ? rule.getActions().stream().map(TaxRuleActionResponse::from).toList() : List.of(),
                matched ? "Rule matched all conditions" : "Rule skipped because one or more conditions did not match");
    }

    private TaxRuleConditionEvaluationResponse evaluateCondition(TaxRuleCondition condition, TaxRuleEvaluationRequest request) {
        String actual = actualValue(condition.getConditionType(), request);
        boolean matched = switch (condition.getOperator()) {
            case EQUALS -> actual != null && actual.equalsIgnoreCase(condition.getValue());
            case NOT_EQUALS -> actual == null || !actual.equalsIgnoreCase(condition.getValue());
            case IN -> actual != null && Arrays.stream(condition.getValue().split(","))
                    .map(String::trim)
                    .anyMatch(expected -> actual.equalsIgnoreCase(expected));
            case IS_TRUE -> Boolean.parseBoolean(actual);
            case IS_FALSE -> !Boolean.parseBoolean(actual);
            case ON_OR_AFTER -> actual != null && LocalDate.parse(actual).compareTo(LocalDate.parse(condition.getValue())) >= 0;
            case ON_OR_BEFORE -> actual != null && LocalDate.parse(actual).compareTo(LocalDate.parse(condition.getValue())) <= 0;
            case BETWEEN -> actual != null
                    && LocalDate.parse(actual).compareTo(LocalDate.parse(condition.getValue())) >= 0
                    && LocalDate.parse(actual).compareTo(LocalDate.parse(condition.getSecondValue())) <= 0;
        };
        return new TaxRuleConditionEvaluationResponse(
                condition.getId(),
                condition.getConditionType(),
                condition.getOperator(),
                expected(condition),
                actual,
                matched,
                condition.getConditionType() + " " + condition.getOperator() + " expected " + expected(condition) + " and actual was " + (actual == null ? "<none>" : actual));
    }

    private String actualValue(TaxRuleConditionType conditionType, TaxRuleEvaluationRequest request) {
        return switch (conditionType) {
            case STORE_JURISDICTION -> request.storeJurisdictionId() == null ? null : request.storeJurisdictionId().toString();
            case SUPPLY_JURISDICTION -> request.supplyJurisdictionId() == null ? null : request.supplyJurisdictionId().toString();
            case PRODUCT_TAX_CATEGORY -> request.productTaxCategoryId() == null ? null : request.productTaxCategoryId().toString();
            case PRODUCT -> request.productId() == null ? null : request.productId().toString();
            case CUSTOMER_EXEMPTION -> Boolean.toString(request.customerExempt());
            case TRANSACTION_DATE -> request.transactionDate() == null ? null : request.transactionDate().toString();
            case SALE_CHANNEL -> request.saleChannel() == null ? null : request.saleChannel().trim().toUpperCase(Locale.ROOT);
        };
    }

    private String expected(TaxRuleCondition condition) {
        return condition.getOperator() == TaxRuleConditionOperator.BETWEEN
                ? condition.getValue() + " and " + condition.getSecondValue()
                : condition.getValue();
    }

    private TaxRuleValues values(
            String code,
            String name,
            String description,
            int priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            List<TaxRuleConditionRequest> conditionRequests,
            List<TaxRuleActionRequest> actionRequests) {
        if (priority < 0) {
            throw new BadRequestException("priority must be zero or greater");
        }
        if (effectiveFrom == null) {
            throw new BadRequestException("effectiveFrom is required");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BadRequestException("effectiveTo must be on or after effectiveFrom");
        }
        List<TaxRuleConditionValues> conditions = (conditionRequests == null ? List.<TaxRuleConditionRequest>of() : conditionRequests).stream()
                .map(this::conditionValues)
                .toList();
        List<TaxRuleActionValues> actions = (actionRequests == null ? List.<TaxRuleActionRequest>of() : actionRequests).stream()
                .map(this::actionValues)
                .toList();
        if (actions.isEmpty()) {
            throw new BadRequestException("At least one tax rule action is required");
        }
        return new TaxRuleValues(
                code,
                TaxGeographySupport.cleanRequired(name, "name"),
                TaxGeographySupport.optionalText(description),
                priority,
                effectiveFrom,
                effectiveTo,
                active,
                conditions,
                actions);
    }

    private TaxRuleConditionValues conditionValues(TaxRuleConditionRequest request) {
        if (request.conditionType() == null) {
            throw new BadRequestException("conditionType is required");
        }
        if (request.operator() == null) {
            throw new BadRequestException("operator is required");
        }
        String value = TaxGeographySupport.optionalText(request.value());
        String secondValue = TaxGeographySupport.optionalText(request.secondValue());
        switch (request.conditionType()) {
            case STORE_JURISDICTION, SUPPLY_JURISDICTION -> {
                requireUuidValue(value, "jurisdiction condition value");
                requireOperator(request.operator(), request.conditionType(), TaxRuleConditionOperator.EQUALS, TaxRuleConditionOperator.NOT_EQUALS, TaxRuleConditionOperator.IN);
                if (request.operator() != TaxRuleConditionOperator.IN && !taxJurisdictionRepository.existsById(UUID.fromString(value))) {
                    throw new NotFoundException("Tax jurisdiction not found");
                }
            }
            case PRODUCT_TAX_CATEGORY -> {
                requireUuidValue(value, "product tax category condition value");
                requireOperator(request.operator(), request.conditionType(), TaxRuleConditionOperator.EQUALS, TaxRuleConditionOperator.NOT_EQUALS, TaxRuleConditionOperator.IN);
                if (request.operator() != TaxRuleConditionOperator.IN) {
                    taxCategoryService.find(UUID.fromString(value));
                }
            }
            case PRODUCT -> {
                requireUuidValue(value, "product condition value");
                requireOperator(request.operator(), request.conditionType(), TaxRuleConditionOperator.EQUALS, TaxRuleConditionOperator.NOT_EQUALS, TaxRuleConditionOperator.IN);
                if (request.operator() != TaxRuleConditionOperator.IN && !productRepository.existsById(UUID.fromString(value))) {
                    throw new NotFoundException("Product not found");
                }
            }
            case CUSTOMER_EXEMPTION -> {
                requireOperator(request.operator(), request.conditionType(), TaxRuleConditionOperator.IS_TRUE, TaxRuleConditionOperator.IS_FALSE);
                value = null;
                secondValue = null;
            }
            case TRANSACTION_DATE -> {
                requireOperator(request.operator(), request.conditionType(), TaxRuleConditionOperator.ON_OR_AFTER, TaxRuleConditionOperator.ON_OR_BEFORE, TaxRuleConditionOperator.BETWEEN);
                requireDateValue(value, "transaction date condition value");
                if (request.operator() == TaxRuleConditionOperator.BETWEEN) {
                    requireDateValue(secondValue, "transaction date condition secondValue");
                    if (LocalDate.parse(secondValue).isBefore(LocalDate.parse(value))) {
                        throw new BadRequestException("transaction date condition secondValue must be on or after value");
                    }
                } else if (secondValue != null) {
                    throw new BadRequestException("secondValue is only allowed for BETWEEN conditions");
                }
            }
            case SALE_CHANNEL -> {
                requireOperator(request.operator(), request.conditionType(), TaxRuleConditionOperator.EQUALS, TaxRuleConditionOperator.NOT_EQUALS, TaxRuleConditionOperator.IN);
                value = TaxGeographySupport.cleanRequired(value, "sale channel condition value").toUpperCase(Locale.ROOT);
            }
        }
        return new TaxRuleConditionValues(request.conditionType(), request.operator(), value, secondValue);
    }

    private TaxRuleActionValues actionValues(TaxRuleActionRequest request) {
        if (request.actionType() == null) {
            throw new BadRequestException("actionType is required");
        }
        TaxGroup group = null;
        TaxComponent component = null;
        String value = TaxGeographySupport.optionalText(request.value());
        switch (request.actionType()) {
            case APPLY_TAX_GROUP -> {
                if (request.taxGroupId() == null) {
                    throw new BadRequestException("taxGroupId is required for APPLY_TAX_GROUP");
                }
                group = taxGroupService.find(request.taxGroupId());
                requireNoComponent(request);
                requireNoActionValue(value, request.actionType());
            }
            case APPLY_TAX_COMPONENT, EXCLUDE_COMPONENT -> {
                if (request.taxComponentId() == null) {
                    throw new BadRequestException("taxComponentId is required for " + request.actionType());
                }
                component = taxComponentService.find(request.taxComponentId());
                requireNoGroup(request);
                requireNoActionValue(value, request.actionType());
            }
            case ZERO_RATE, EXEMPT, OUT_OF_SCOPE -> {
                requireNoGroup(request);
                requireNoComponent(request);
                requireNoActionValue(value, request.actionType());
            }
            case INCLUDED_PRICE_BEHAVIOR -> {
                requireNoGroup(request);
                requireNoComponent(request);
                value = TaxGeographySupport.cleanRequired(value, "included price behavior").toUpperCase(Locale.ROOT);
                parseIncludedPriceBehavior(value);
            }
            case ROUNDING_STRATEGY -> {
                requireNoGroup(request);
                requireNoComponent(request);
                value = TaxGeographySupport.cleanRequired(value, "rounding strategy").toUpperCase(Locale.ROOT);
                parseRoundingStrategy(value);
            }
        }
        return new TaxRuleActionValues(request.actionType(), group, component, value);
    }

    private void requireOperator(TaxRuleConditionOperator actual, TaxRuleConditionType conditionType, TaxRuleConditionOperator... allowed) {
        if (Arrays.stream(allowed).noneMatch(operator -> operator == actual)) {
            throw new BadRequestException("operator " + actual + " is not valid for " + conditionType);
        }
    }

    private void requireUuidValue(String value, String field) {
        String cleaned = TaxGeographySupport.cleanRequired(value, field);
        if (cleaned.contains(",")) {
            Arrays.stream(cleaned.split(",")).map(String::trim).forEach(this::parseUuid);
        } else {
            parseUuid(cleaned);
        }
    }

    private void parseUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("condition value must be a UUID");
        }
    }

    private void parseIncludedPriceBehavior(String value) {
        try {
            IncludedPriceBehavior.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("included price behavior is not supported");
        }
    }

    private void parseRoundingStrategy(String value) {
        try {
            TaxRoundingStrategy.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("rounding strategy is not supported");
        }
    }

    private void requireDateValue(String value, String field) {
        try {
            LocalDate.parse(TaxGeographySupport.cleanRequired(value, field));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BadRequestException(field + " must be an ISO date");
        }
    }

    private void requireNoGroup(TaxRuleActionRequest request) {
        if (request.taxGroupId() != null) {
            throw new BadRequestException("taxGroupId is not allowed for " + request.actionType());
        }
    }

    private void requireNoComponent(TaxRuleActionRequest request) {
        if (request.taxComponentId() != null) {
            throw new BadRequestException("taxComponentId is not allowed for " + request.actionType());
        }
    }

    private void requireNoActionValue(String value, TaxRuleActionType actionType) {
        if (value != null) {
            throw new BadRequestException("value is not allowed for " + actionType);
        }
    }

    private TaxRule save(TaxRule rule) {
        try {
            return taxRuleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<TaxRule> specification(TaxRuleSearchRequest request) {
        Specification<TaxRule> spec = Specification
                .where(TaxGeographySupport.<TaxRule>equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
        if (request.effectiveOn() != null) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.lessThanOrEqualTo(root.get("effectiveFrom"), request.effectiveOn()),
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("effectiveTo")),
                            criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveTo"), request.effectiveOn()))));
        }
        return spec;
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_RULE", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax rule code already exists");
    }
}
