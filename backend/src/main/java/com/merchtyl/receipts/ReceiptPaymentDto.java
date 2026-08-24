package com.merchtyl.receipts;

import com.merchtyl.sales.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptPaymentDto(
        UUID id,
        PaymentMethod method,
        BigDecimal amount,
        BigDecimal cashTendered,
        BigDecimal changeDue,
        String reference,
        Instant completedAt
) {
}
