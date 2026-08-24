package com.merchtyl.tax;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TaxRuleUpdateRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        int priority,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        @Valid List<TaxRuleConditionRequest> conditions,
        @Valid List<TaxRuleActionRequest> actions,
        @NotNull Long version) {
}
