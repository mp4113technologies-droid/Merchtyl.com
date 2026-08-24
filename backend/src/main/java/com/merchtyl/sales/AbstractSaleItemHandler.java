package com.merchtyl.sales;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;

import java.math.BigDecimal;

abstract class AbstractSaleItemHandler implements SaleItemHandler {
    protected void validateCommon(SaleItemRequest request) {
        if (request == null) {
            throw new BadRequestException("Sale item request is required");
        }
        Product product = request.product();
        if (product == null) {
            throw new BadRequestException("Product is required");
        }
        if (product.getSellableType() != supportedType()) {
            throw new BadRequestException("Handler does not support product sellable type");
        }
        if (!product.isActive()) {
            throw new BadRequestException("Product is inactive");
        }
        requirePositive(request.quantity(), "quantity");
        requireNonNegative(request.unitPrice(), "unitPrice");
        requireNonNegative(request.discountAmount(), "discountAmount");
        if (hasDecimalScale(request.quantity()) && !product.hasCapability(ProductCapability.ALLOW_DECIMAL_QUANTITY)) {
            throw new BadRequestException("Product does not allow decimal quantities");
        }
        if (isPositive(request.discountAmount()) && !product.hasCapability(ProductCapability.ALLOW_DISCOUNT)) {
            throw new BadRequestException("Product does not allow discounts");
        }
        if (request.priceOverride() && !product.hasCapability(ProductCapability.ALLOW_PRICE_OVERRIDE)) {
            throw new BadRequestException("Product does not allow price overrides");
        }
        if (product.hasCapability(ProductCapability.REQUIRE_AGE_VERIFICATION) && !request.ageVerified()) {
            throw new BadRequestException("AGE_VERIFICATION_REQUIRED");
        }
        if (product.hasCapability(ProductCapability.REQUIRE_SERIAL_NUMBER) && isBlank(request.serialNumber())) {
            throw new BadRequestException("Serial number is required");
        }
        if (product.hasCapability(ProductCapability.REQUIRE_EXTERNAL_REFERENCE) && isBlank(request.externalReference())) {
            throw new BadRequestException("External reference is required");
        }
        if (product.hasCapability(ProductCapability.REQUIRE_CUSTOMER) && request.customerId() == null) {
            throw new BadRequestException("Customer is required");
        }
        if (product.hasCapability(ProductCapability.RESTRICT_PAYMENT_METHOD) && isBlank(request.paymentMethodCode())) {
            throw new BadRequestException("Payment method is required for restricted products");
        }
    }

    protected static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(field + " must be greater than zero");
        }
    }

    protected static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(field + " must be zero or greater");
        }
    }

    private static boolean hasDecimalScale(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() > 0;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
