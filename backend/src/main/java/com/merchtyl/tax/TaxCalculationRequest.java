package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxCalculationRequest(
        UUID storeId,
        UUID storeJurisdictionId,
        UUID supplyJurisdictionId,
        UUID productId,
        UUID productTaxCategoryId,
        boolean customerExempt,
        @NotNull LocalDate transactionDate,
        String saleChannel,
        @NotNull BigDecimal unitPrice,
        @NotNull BigDecimal quantity,
        BigDecimal discountAmount,
        Boolean pricesIncludeTax,
        String currencyCode) {
}
