package com.merchtyl.receipts;

import com.merchtyl.register.RegisterType;
import com.merchtyl.sales.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceHistorySummary(
        UUID saleId,
        String receiptNumber,
        Instant completedAt,
        RegisterType saleType,
        UUID storeId,
        String storeName,
        UUID registerId,
        String registerCode,
        String cashierName,
        String customerName,
        String orderNumber,
        BigDecimal totalAmount,
        String currencyCode,
        String paymentSummary,
        SaleStatus status) {
}
