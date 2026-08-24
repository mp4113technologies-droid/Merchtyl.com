package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record TaxRuleEvaluationRequest(
        UUID storeJurisdictionId,
        UUID supplyJurisdictionId,
        UUID productTaxCategoryId,
        UUID productId,
        boolean customerExempt,
        @NotNull LocalDate transactionDate,
        String saleChannel) {
}
