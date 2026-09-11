package com.merchtyl.returns;

import java.math.BigDecimal;
import java.util.UUID;
import com.merchtyl.product.DepositType;

public record ReturnItemResponse(
        UUID id,
        UUID originalSaleItemId,
        UUID productId,
        int lineNumber,
        String productSku,
        String productName,
        BigDecimal quantity,
        String reason,
        BigDecimal originalQuantity,
        BigDecimal originalUnitPrice,
        BigDecimal originalDiscountAmount,
        BigDecimal originalLineSubtotal,
        BigDecimal originalTaxAmount,
        BigDecimal originalLineTotal,
        BigDecimal originalProductCost,
        BigDecimal originalProductPrice,
        String originalProductCapabilities,
        UUID originalProductTaxCategoryId,
        BigDecimal returnSubtotalAmount,
        BigDecimal returnTaxAmount,
        BigDecimal returnTotalAmount,
        DepositType originalDepositType,
        BigDecimal originalDepositUnitAmount,
        BigDecimal originalDepositQuantity,
        BigDecimal originalDepositTotal,
        BigDecimal returnDepositTotal,
        long version
) {
    public ReturnItemResponse(UUID id, UUID originalSaleItemId, UUID productId, int lineNumber, String productSku,
            String productName, BigDecimal quantity, String reason, BigDecimal originalQuantity,
            BigDecimal originalUnitPrice, BigDecimal originalDiscountAmount, BigDecimal originalLineSubtotal,
            BigDecimal originalTaxAmount, BigDecimal originalLineTotal, BigDecimal originalProductCost,
            BigDecimal originalProductPrice, String originalProductCapabilities, UUID originalProductTaxCategoryId,
            BigDecimal returnSubtotalAmount, BigDecimal returnTaxAmount, BigDecimal returnTotalAmount, long version) {
        this(id, originalSaleItemId, productId, lineNumber, productSku, productName, quantity, reason,
                originalQuantity, originalUnitPrice, originalDiscountAmount, originalLineSubtotal, originalTaxAmount,
                originalLineTotal, originalProductCost, originalProductPrice, originalProductCapabilities,
                originalProductTaxCategoryId, returnSubtotalAmount, returnTaxAmount, returnTotalAmount,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, version);
    }
    static ReturnItemResponse from(ReturnItem item) {
        return new ReturnItemResponse(
                item.getId(),
                item.getOriginalSaleItem().getId(),
                item.getProduct() == null ? null : item.getProduct().getId(),
                item.getLineNumber(),
                item.getProductSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getReason(),
                item.getOriginalQuantity(),
                item.getOriginalUnitPrice(),
                item.getOriginalDiscountAmount(),
                item.getOriginalLineSubtotal(),
                item.getOriginalTaxAmount(),
                item.getOriginalLineTotal(),
                item.getOriginalProductCost(),
                item.getOriginalProductPrice(),
                item.getOriginalProductCapabilities(),
                item.getOriginalProductTaxCategoryId(),
                item.getReturnSubtotalAmount(),
                item.getReturnTaxAmount(),
                item.getReturnTotalAmount(),
                item.getOriginalDepositType(),
                item.getOriginalDepositUnitAmount(),
                item.getOriginalDepositQuantity(),
                item.getOriginalDepositTotal(),
                item.getReturnDepositTotal(),
                item.getVersion());
    }
}
