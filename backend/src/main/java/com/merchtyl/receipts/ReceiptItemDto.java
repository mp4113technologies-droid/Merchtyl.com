package com.merchtyl.receipts;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiptItemDto(
        UUID id,
        UUID productId,
        int lineNumber,
        String productSku,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal completedProductCost,
        BigDecimal completedProductPrice,
        String completedProductCapabilities,
        BigDecimal discountAmount,
        BigDecimal lineSubtotal,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {
}
