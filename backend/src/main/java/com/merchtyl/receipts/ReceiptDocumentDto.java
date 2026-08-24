package com.merchtyl.receipts;

import com.merchtyl.sales.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReceiptDocumentDto(
        String brandName,
        String brandTagline,
        ReceiptStoreDto store,
        ReceiptRegisterDto register,
        ReceiptCashierDto cashier,
        String receiptNumber,
        UUID saleId,
        String saleNumber,
        LocalDate businessDate,
        Instant completedAt,
        String currencyCode,
        List<ReceiptItemDto> items,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        List<ReceiptTaxSummaryDto> taxSummaries,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        List<ReceiptPaymentDto> payments,
        BigDecimal cashTendered,
        BigDecimal changeDue
) {
}
