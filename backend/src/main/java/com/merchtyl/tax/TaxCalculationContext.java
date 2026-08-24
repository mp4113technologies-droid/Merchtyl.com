package com.merchtyl.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record TaxCalculationContext(
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
        boolean pricesIncludeTax) {
    BigDecimal lineAmount() {
        return unitPrice.multiply(quantity).subtract(discountAmount);
    }

    BigDecimal lineSubtotal() {
        return unitPrice.multiply(quantity);
    }
}
