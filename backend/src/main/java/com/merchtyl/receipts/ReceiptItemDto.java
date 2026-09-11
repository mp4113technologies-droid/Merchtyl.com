package com.merchtyl.receipts;

import java.math.BigDecimal;
import java.util.UUID;
import com.merchtyl.product.DepositType;

public record ReceiptItemDto(
        UUID id,
        UUID productId,
        int lineNumber,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal completedProductCost,
        BigDecimal completedProductPrice,
        String completedProductCapabilities,
        BigDecimal discountAmount,
        BigDecimal lineSubtotal,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        UUID promotionId,
        String promotionName,
        BigDecimal promotionDiscountAmount,
        DepositType depositType,
        BigDecimal depositUnitAmount,
        BigDecimal depositQuantity,
        BigDecimal depositTotal,
        java.util.List<com.merchtyl.sales.FoodComponentSnapshot> foodMenuComponents,
        java.util.List<String> foodMenuModifiers
) {
    public ReceiptItemDto(UUID id, UUID productId, int lineNumber, String ignoredProductSku,
            String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal completedProductCost,
            BigDecimal completedProductPrice, String completedProductCapabilities, BigDecimal discountAmount,
            BigDecimal lineSubtotal, BigDecimal taxAmount, BigDecimal lineTotal) {
        this(id, productId, lineNumber, productName, quantity, unitPrice, completedProductCost,
                completedProductPrice, completedProductCapabilities, discountAmount, lineSubtotal, taxAmount, lineTotal,
                null,null,null,null,null,BigDecimal.ZERO,BigDecimal.ZERO,java.util.List.of(),java.util.List.of());
    }
}
