package com.merchtyl.discount;

import com.merchtyl.sales.SaleAdjustmentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DiscountDefinitionResponse(UUID id, String name, SaleAdjustmentType type, BigDecimal value,
 String description, BigDecimal minimumPurchaseAmount, BigDecimal maximumPurchaseAmount, BigDecimal maximumDiscountAmount,
 Integer minimumQuantity, Set<UUID> eligibleCategoryIds, Set<UUID> eligibleProductIds, boolean allStores, Set<UUID> storeIds,
 Instant startsAt, Instant endsAt, boolean active, Instant createdAt, Instant updatedAt, long version) {
    static DiscountDefinitionResponse from(DiscountDefinition value) {
        return new DiscountDefinitionResponse(value.getId(), value.getName(), value.getType(), value.getValue(), value.getDescription(),
                value.getMinimumPurchaseAmount(),value.getMaximumPurchaseAmount(),value.getMaximumDiscountAmount(),value.getMinimumQuantity(),
                value.getEligibleCategoryIds(),value.getEligibleProductIds(),value.isAllStores(),value.getStoreIds(),value.getStartsAt(),value.getEndsAt(),
                value.isActive(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion());
    }
}
