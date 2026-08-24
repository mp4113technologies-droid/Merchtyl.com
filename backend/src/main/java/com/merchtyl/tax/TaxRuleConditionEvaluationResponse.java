package com.merchtyl.tax;

import java.util.UUID;

public record TaxRuleConditionEvaluationResponse(
        UUID conditionId,
        TaxRuleConditionType conditionType,
        TaxRuleConditionOperator operator,
        String expected,
        String actual,
        boolean matched,
        String explanation) {
}
