package com.merchtyl.reports;

import com.merchtyl.sales.PaymentMethod;

import java.math.BigDecimal;

public record SalesReportPaymentBreakdown(
        PaymentMethod method,
        BigDecimal collected,
        BigDecimal refunded,
        BigDecimal net
) {
}
