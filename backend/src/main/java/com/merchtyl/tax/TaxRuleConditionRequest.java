package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxRuleConditionRequest(
        @NotNull TaxRuleConditionType conditionType,
        @NotNull TaxRuleConditionOperator operator,
        String value,
        String secondValue) {
}
