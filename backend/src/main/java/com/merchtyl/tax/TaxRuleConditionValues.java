package com.merchtyl.tax;

record TaxRuleConditionValues(
        TaxRuleConditionType conditionType,
        TaxRuleConditionOperator operator,
        String value,
        String secondValue) {
}
