package com.merchtyl.sales;

import com.merchtyl.product.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(
        Product product,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        boolean priceOverride,
        boolean ageVerified,
        String serialNumber,
        String externalReference,
        UUID customerId,
        String paymentMethodCode
) {
}
