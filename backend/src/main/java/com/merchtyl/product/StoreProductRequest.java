package com.merchtyl.product;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record StoreProductRequest(@NotNull UUID storeId, boolean active, boolean sellable,
 @NotNull @PositiveOrZero BigDecimal sellingPrice, @PositiveOrZero BigDecimal costPrice,
 @PositiveOrZero BigDecimal minimumSellingPrice, @PositiveOrZero BigDecimal lowStockThreshold,
 boolean allowDiscount, boolean allowPriceOverride) {}
