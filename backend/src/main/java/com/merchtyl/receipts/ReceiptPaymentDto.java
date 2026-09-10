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
        BigDecimal cashRoundingAdjustment,
        BigDecimal cashSettlementAmount,
        BigDecimal changeDue,
        String reference,
        Instant completedAt
) {
    public ReceiptPaymentDto(UUID id, PaymentMethod method, BigDecimal amount, BigDecimal cashTendered,
            BigDecimal changeDue, String reference, Instant completedAt) {
        this(id, method, amount, cashTendered, BigDecimal.ZERO.setScale(2),
                method == PaymentMethod.CASH ? amount : null, changeDue, reference, completedAt);
    }
}
