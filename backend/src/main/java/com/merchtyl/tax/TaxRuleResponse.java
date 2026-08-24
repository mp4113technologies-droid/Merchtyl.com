package com.merchtyl.tax;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaxRuleResponse(
        UUID id,
        String code,
        String name,
        String description,
        int priority,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        List<TaxRuleConditionResponse> conditions,
        List<TaxRuleActionResponse> actions,
        Instant createdAt,
        Instant updatedAt,
        long version) {
    static TaxRuleResponse from(TaxRule rule) {
        return new TaxRuleResponse(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getDescription(),
                rule.getPriority(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.isActive(),
                rule.getConditions().stream().map(TaxRuleConditionResponse::from).toList(),
                rule.getActions().stream().map(TaxRuleActionResponse::from).toList(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                rule.getVersion());
    }
}
