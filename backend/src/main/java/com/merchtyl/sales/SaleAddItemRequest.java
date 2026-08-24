package com.merchtyl.sales;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleAddItemRequest(
        @NotNull UUID productId,
        UUID variantId,
        @NotNull BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        Boolean priceOverride,
        Boolean ageVerified,
        String serialNumber,
        String externalReference,
        UUID customerId,
        String paymentMethodCode
) {
    public SaleAddItemRequest(UUID productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountAmount,
                              Boolean priceOverride, Boolean ageVerified, String serialNumber, String externalReference,
                              UUID customerId, String paymentMethodCode) {
        this(productId, null, quantity, unitPrice, discountAmount, priceOverride, ageVerified, serialNumber,
                externalReference, customerId, paymentMethodCode);
    }
}
