package com.merchtyl.refunds;

import com.merchtyl.sales.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundPaymentResponse(
        UUID id,
        UUID originalPaymentId,
        int lineNumber,
        PaymentMethod method,
        BigDecimal amount,
        String currencyCode,
        String reference,
        String notes,
        long version
) {
    static RefundPaymentResponse from(RefundPayment payment) {
        return new RefundPaymentResponse(
                payment.getId(),
                payment.getOriginalPayment() == null ? null : payment.getOriginalPayment().getId(),
                payment.getLineNumber(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                payment.getReference(),
                payment.getNotes(),
                payment.getVersion());
    }
}
