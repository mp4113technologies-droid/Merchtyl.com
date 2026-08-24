package com.merchtyl.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaxCalculationResponse(
        UUID storeId,
        UUID storeJurisdictionId,
        UUID supplyJurisdictionId,
        UUID productId,
        UUID productTaxCategoryId,
        LocalDate transactionDate,
        String saleChannel,
        String currencyCode,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        boolean pricesIncludeTax,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        boolean zeroRated,
        boolean exempt,
        boolean outOfScope,
        IncludedPriceBehavior includedPriceBehavior,
        TaxRoundingStrategy roundingStrategy,
        List<TaxComponentCalculationResponse> components,
        List<String> explanations,
        TaxRuleEvaluationResponse ruleEvaluation) {
}
