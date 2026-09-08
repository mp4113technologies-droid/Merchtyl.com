package com.merchtyl.discount;

import com.merchtyl.sales.SaleAdjustmentType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DiscountDefinitionRequest(
        @NotBlank @Size(max=120) String name,
        @NotNull SaleAdjustmentType type,
        @NotNull @Positive BigDecimal value,
        @Size(max=500) String description,
        @PositiveOrZero BigDecimal minimumPurchaseAmount,
        @PositiveOrZero BigDecimal maximumPurchaseAmount,
        @PositiveOrZero BigDecimal maximumDiscountAmount,
        @Positive Integer minimumQuantity,
        Set<UUID> eligibleCategoryIds,
        Set<UUID> eligibleProductIds,
        boolean allStores,
        Set<UUID> storeIds,
        Instant startsAt,
        Instant endsAt,
        boolean active
) {
    public DiscountDefinitionRequest(String name, SaleAdjustmentType type, BigDecimal value, String description, boolean active) {
        this(name, type, value, description, null, null, null, null, Set.of(), Set.of(), true, Set.of(), null, null, active);
    }
}
