package com.merchtyl.tax;

import com.merchtyl.common.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaxRuleEvaluator {
    private final TaxRuleRepository taxRuleRepository;

    public TaxRuleEvaluator(TaxRuleRepository taxRuleRepository) {
        this.taxRuleRepository = taxRuleRepository;
    }

    @Transactional(readOnly = true)
    public TaxRuleEvaluationResponse evaluate(TaxRuleEvaluationRequest request) {
        if (request.transactionDate() == null) {
            throw new BadRequestException("transactionDate is required");
        }
        List<TaxRuleMatchResponse> matches = taxRuleRepository.findActiveEffectiveRules(request.transactionDate()).stream()
                .map(rule -> evaluateRule(rule, request))
                .toList();

        LinkedHashSet<UUID> groups = new LinkedHashSet<>();
        LinkedHashSet<UUID> components = new LinkedHashSet<>();
        LinkedHashSet<UUID> excludedComponents = new LinkedHashSet<>();
        boolean zeroRated = false;
        boolean exempt = false;
        boolean outOfScope = false;
        IncludedPriceBehavior includedPriceBehavior = IncludedPriceBehavior.USE_RATE_SETTING;
        TaxRoundingStrategy roundingStrategy = TaxRoundingStrategy.HALF_UP;

        for (TaxRuleMatchResponse match : matches) {
            if (!match.matched()) {
                continue;
            }
            for (TaxRuleActionResponse action : match.actions()) {
                switch (action.actionType()) {
                    case APPLY_TAX_GROUP -> groups.add(action.taxGroupId());
                    case APPLY_TAX_COMPONENT -> components.add(action.taxComponentId());
                    case EXCLUDE_COMPONENT -> {
                        excludedComponents.add(action.taxComponentId());
                        components.remove(action.taxComponentId());
                    }
                    case ZERO_RATE -> zeroRated = true;
                    case EXEMPT -> exempt = true;
                    case OUT_OF_SCOPE -> outOfScope = true;
                    case INCLUDED_PRICE_BEHAVIOR -> includedPriceBehavior = IncludedPriceBehavior.valueOf(action.value());
                    case ROUNDING_STRATEGY -> roundingStrategy = TaxRoundingStrategy.valueOf(action.value());
                }
            }
        }

        return new TaxRuleEvaluationResponse(
                List.copyOf(groups),
                List.copyOf(components),
                List.copyOf(excludedComponents),
                zeroRated,
                exempt,
                outOfScope,
                includedPriceBehavior,
                roundingStrategy,
                matches);
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
}
