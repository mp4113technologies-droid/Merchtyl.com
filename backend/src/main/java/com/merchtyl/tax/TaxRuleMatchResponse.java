package com.merchtyl.tax;

import java.util.List;
import java.util.UUID;

public record TaxRuleMatchResponse(
        UUID ruleId,
        String code,
        String name,
        int priority,
        boolean matched,
        List<TaxRuleConditionEvaluationResponse> conditions,
        List<TaxRuleActionResponse> actions,
        String explanation) {
}
