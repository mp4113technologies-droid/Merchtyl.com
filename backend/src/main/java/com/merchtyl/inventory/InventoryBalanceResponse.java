package com.merchtyl.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryBalanceResponse(
        UUID id,
        UUID storeId,
        UUID productId,
        BigDecimal quantityOnHand,
        Instant lastTransactionAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    static InventoryBalanceResponse from(InventoryBalance balance) {
        return new InventoryBalanceResponse(
                balance.getId(),
                balance.getStore().getId(),
                balance.getProduct().getId(),
                balance.getQuantityOnHand(),
                balance.getLastTransactionAt(),
                balance.getCreatedAt(),
                balance.getUpdatedAt(),
                balance.getVersion());
    }

    static InventoryBalanceResponse zero(UUID storeId, UUID productId) {
        return new InventoryBalanceResponse(
                null,
                storeId,
                productId,
                BigDecimal.ZERO.setScale(4),
                null,
                null,
                null,
                null);
    }
}
