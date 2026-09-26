package com.merchtyl.sales;

import java.math.BigDecimal;

public record SalesClassification(
        BigDecimal taxableSales,
        BigDecimal nonTaxableSales,
        BigDecimal taxCollected,
        BigDecimal merchandiseNetSales) {

    public static SalesClassification zero() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new SalesClassification(zero, zero, zero, zero);
    }
}
