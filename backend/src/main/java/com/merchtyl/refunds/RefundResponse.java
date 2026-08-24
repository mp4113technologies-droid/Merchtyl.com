package com.merchtyl.refunds;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID returnId,
        UUID originalSaleId,
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        UUID createdBy,
        LocalDate businessDate,
        Instant occurredAt,
        String currencyCode,
        String reason,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        UUID approvedBy,
        Instant approvedAt,
        String approvalNotes,
        List<RefundPaymentResponse> payments,
        List<RefundItemTaxResponse> itemTaxes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getReturnRecord().getId(),
                refund.getOriginalSale().getId(),
                refund.getStore().getId(),
                refund.getRegister().getId(),
                refund.getRegisterSession().getId(),
                refund.getCreatedBy().getId(),
                refund.getBusinessDate(),
                refund.getOccurredAt(),
                refund.getCurrencyCode(),
                refund.getReason(),
                refund.getSubtotalAmount(),
                refund.getTaxAmount(),
                refund.getTotalAmount(),
                refund.getApprovedBy() == null ? null : refund.getApprovedBy().getId(),
                refund.getApprovedAt(),
                refund.getApprovalNotes(),
                refund.getPayments().stream().map(RefundPaymentResponse::from).toList(),
                refund.getItemTaxes().stream().map(RefundItemTaxResponse::from).toList(),
                refund.getCreatedAt(),
                refund.getUpdatedAt(),
                refund.getVersion());
    }
}
