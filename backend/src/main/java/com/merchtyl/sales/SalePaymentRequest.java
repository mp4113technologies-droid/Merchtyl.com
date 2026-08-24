package com.merchtyl.sales;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalePaymentRequest(
        @NotNull PaymentMethod method,
        @NotNull BigDecimal amount,
        BigDecimal cashTendered,
        String reference,
        String notes
) {
}
