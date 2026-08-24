package com.merchtyl.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        PaymentMethod method,
        BigDecimal amount,
        String currencyCode,
        BigDecimal cashTendered,
        BigDecimal changeDue,
        String reference,
        String notes,
        UUID createdBy,
        Instant completedAt,
        Instant createdAt,
        long version
) {
    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                payment.getCashTendered(),
                payment.getChangeDue(),
                payment.getReference(),
                payment.getNotes(),
                payment.getCreatedBy().getId(),
                payment.getCompletedAt(),
                payment.getCreatedAt(),
                payment.getVersion());
    }
}
