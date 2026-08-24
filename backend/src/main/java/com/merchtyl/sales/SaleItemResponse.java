package com.merchtyl.sales;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        UUID productId,
        UUID variantId,
        int lineNumber,
        String productSku,
        String productName,
        String variantSku,
        String variantName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal completedProductCost,
        BigDecimal completedProductPrice,
        String completedProductCapabilities,
        boolean priceOverride,
        boolean ageVerified,
        String serialNumber,
        String externalReference,
        UUID customerId,
        String paymentMethodCode,
        BigDecimal lineSubtotal,
        BigDecimal estimatedTaxAmount,
        BigDecimal lineTotal,
        long version
) {
    public SaleItemResponse(UUID id, UUID productId, int lineNumber, String productSku, String productName,
                            BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountAmount,
                            BigDecimal completedProductCost, BigDecimal completedProductPrice,
                            String completedProductCapabilities, boolean priceOverride, boolean ageVerified,
                            String serialNumber, String externalReference, UUID customerId, String paymentMethodCode,
                            BigDecimal lineSubtotal, BigDecimal estimatedTaxAmount, BigDecimal lineTotal, long version) {
        this(id, productId, null, lineNumber, productSku, productName, null, null, quantity, unitPrice,
                discountAmount, completedProductCost, completedProductPrice, completedProductCapabilities,
                priceOverride, ageVerified, serialNumber, externalReference, customerId, paymentMethodCode,
                lineSubtotal, estimatedTaxAmount, lineTotal, version);
    }

    static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getVariant() == null ? null : item.getVariant().getId(),
                item.getLineNumber(),
                item.getProductSku(),
                item.getProductName(),
                item.getVariantSku(),
                item.getVariantName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getCompletedProductCost(),
                item.getCompletedProductPrice(),
                item.getCompletedProductCapabilities(),
                item.isPriceOverride(),
                item.isAgeVerified(),
                item.getSerialNumber(),
                item.getExternalReference(),
                item.getCustomerId(),
                item.getPaymentMethodCode(),
                item.getLineSubtotal(),
                item.getEstimatedTaxAmount(),
                item.getLineTotal(),
                item.getVersion());
    }
}
