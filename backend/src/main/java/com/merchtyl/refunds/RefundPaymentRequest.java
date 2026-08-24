package com.merchtyl.refunds;

import com.merchtyl.sales.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundPaymentRequest(
        @NotNull PaymentMethod method,
        @NotNull BigDecimal amount,
        UUID originalPaymentId,
        @Size(max = 120) String reference,
        @Size(max = 500) String notes
) {
}
