package com.merchtyl.refunds;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundItemTaxResponse(
        UUID id,
        UUID returnItemId,
        UUID originalSaleItemId,
        int lineNumber,
        UUID productTaxCategoryId,
        String taxComponentCode,
        String taxComponentName,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        String currencyCode,
        long version
) {
    static RefundItemTaxResponse from(RefundItemTax tax) {
        return new RefundItemTaxResponse(
                tax.getId(),
                tax.getReturnItem().getId(),
                tax.getOriginalSaleItem().getId(),
                tax.getLineNumber(),
                tax.getProductTaxCategoryId(),
                tax.getTaxComponentCode(),
                tax.getTaxComponentName(),
                tax.getTaxableAmount(),
                tax.getTaxAmount(),
                tax.getCurrencyCode(),
                tax.getVersion());
    }
}
