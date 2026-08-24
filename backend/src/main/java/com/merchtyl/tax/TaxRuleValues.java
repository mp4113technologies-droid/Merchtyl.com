package com.merchtyl.tax;

import java.time.LocalDate;
import java.util.List;

record TaxRuleValues(
        String code,
        String name,
        String description,
        int priority,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        List<TaxRuleConditionValues> conditions,
        List<TaxRuleActionValues> actions) {
}
