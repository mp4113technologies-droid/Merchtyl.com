package com.merchtyl.tax;

import java.util.List;
import java.util.UUID;

public record TaxRuleEvaluationResponse(
        List<UUID> appliedTaxGroupIds,
        List<UUID> appliedTaxComponentIds,
        List<UUID> excludedTaxComponentIds,
        boolean zeroRated,
        boolean exempt,
        boolean outOfScope,
        IncludedPriceBehavior includedPriceBehavior,
        TaxRoundingStrategy roundingStrategy,
        List<TaxRuleMatchResponse> ruleMatches) {
}
