package com.merchtyl.sales;

import java.math.BigDecimal;

public record SalesClassification(
        BigDecimal taxableSales,
        BigDecimal nonTaxableSales,
        BigDecimal generalTaxCollected,
        BigDecimal vapeTaxCollected,
        BigDecimal totalTaxCollected,
        BigDecimal merchandiseNetSales) {

    public SalesClassification(BigDecimal taxableSales, BigDecimal nonTaxableSales,
                               BigDecimal taxCollected, BigDecimal merchandiseNetSales) {
        this(taxableSales, nonTaxableSales, taxCollected, BigDecimal.ZERO.setScale(2), taxCollected, merchandiseNetSales);
    }

    public static SalesClassification zero() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new SalesClassification(zero, zero, zero, zero, zero, zero);
    }

    /** Backward-compatible name used by existing consumers. */
    public BigDecimal taxCollected() { return totalTaxCollected; }
}
