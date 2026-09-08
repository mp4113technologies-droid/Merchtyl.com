package com.merchtyl.sales;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleCheckoutDiscountRequest(
        UUID discountDefinitionId,
        SaleAdjustmentType type,
        BigDecimal value,
        String reason
) {
    public SaleCheckoutDiscountRequest(SaleAdjustmentType type, BigDecimal value, String reason) {
        this(null, type, value, reason);
    }
}
