package com.merchtyl.tax;

import java.util.UUID;

public record TaxRuleConditionResponse(
        UUID id,
        TaxRuleConditionType conditionType,
        TaxRuleConditionOperator operator,
        String value,
        String secondValue) {
    static TaxRuleConditionResponse from(TaxRuleCondition condition) {
        return new TaxRuleConditionResponse(
                condition.getId(),
                condition.getConditionType(),
                condition.getOperator(),
                condition.getValue(),
                condition.getSecondValue());
    }
}
