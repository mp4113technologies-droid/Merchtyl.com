package com.merchtyl.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        UUID createdBy,
        UUID customerId,
        SaleStatus status,
        LocalDate businessDate,
        String saleChannel,
        String currencyCode,
        boolean pricesIncludeTax,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal estimatedTaxAmount,
        BigDecimal totalAmount,
        Instant heldAt,
        Instant cancelledAt,
        UUID completedBy,
        Instant completedAt,
        List<SaleItemResponse> items,
        List<PaymentResponse> payments,
        BigDecimal paidAmount,
        BigDecimal balanceDue,
        BigDecimal changeDue,
        boolean paymentComplete,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static SaleResponse from(Sale sale) {
        BigDecimal paidAmount = money(sale.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal changeDue = money(sale.getPayments().stream()
                .map(Payment::getChangeDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal balanceDue = money(sale.getTotalAmount().subtract(paidAmount).max(BigDecimal.ZERO));
        return new SaleResponse(
                sale.getId(),
                sale.getStore().getId(),
                sale.getRegister().getId(),
                sale.getRegisterSession().getId(),
                sale.getCreatedBy().getId(),
                sale.getCustomerId(),
                sale.getStatus(),
                sale.getBusinessDate(),
                sale.getSaleChannel(),
                sale.getCurrencyCode(),
                sale.isPricesIncludeTax(),
                sale.getSubtotalAmount(),
                sale.getDiscountAmount(),
                sale.getEstimatedTaxAmount(),
                sale.getTotalAmount(),
                sale.getHeldAt(),
                sale.getCancelledAt(),
                sale.getCompletedBy() == null ? null : sale.getCompletedBy().getId(),
                sale.getCompletedAt(),
                sale.getItems().stream().map(SaleItemResponse::from).toList(),
                sale.getPayments().stream().map(PaymentResponse::from).toList(),
                paidAmount,
                balanceDue,
                changeDue,
                sale.getTotalAmount().signum() > 0 && balanceDue.signum() == 0,
                sale.getCreatedAt(),
                sale.getUpdatedAt(),
                sale.getVersion());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
