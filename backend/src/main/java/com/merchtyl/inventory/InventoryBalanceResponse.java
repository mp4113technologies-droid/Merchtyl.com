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
        Long version,
        UUID variantId
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
                balance.getVersion(),balance.getVariant()==null?null:balance.getVariant().getId());
    }

    static InventoryBalanceResponse zero(UUID storeId, UUID productId) {
        return zero(storeId, productId, null);
    }

    static InventoryBalanceResponse zero(UUID storeId, UUID productId, UUID variantId) {
        return new InventoryBalanceResponse(
                null,
                storeId,
                productId,
                BigDecimal.ZERO.setScale(4),
                null,
                null,
                null,
                null,variantId);
    }

    public InventoryBalanceResponse(UUID id,UUID storeId,UUID productId,BigDecimal quantityOnHand,Instant lastTransactionAt,Instant createdAt,Instant updatedAt,Long version){this(id,storeId,productId,quantityOnHand,lastTransactionAt,createdAt,updatedAt,version,null);}
}
