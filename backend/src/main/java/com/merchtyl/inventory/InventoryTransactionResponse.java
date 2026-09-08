package com.merchtyl.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        UUID balanceId,
        UUID storeId,
        UUID productId,
        InventoryTransactionType transactionType,
        BigDecimal quantityDelta,
        BigDecimal resultingQuantity,
        String referenceType,
        UUID referenceId,
        String reason,
        UUID actorUserId,
        Instant occurredAt,
        Instant createdAt,
        long version,
        UUID variantId
) {
    static InventoryTransactionResponse from(InventoryTransaction transaction) {
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getBalance().getId(),
                transaction.getStore().getId(),
                transaction.getProduct().getId(),
                transaction.getTransactionType(),
                transaction.getQuantityDelta(),
                transaction.getResultingQuantity(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getReason(),
                transaction.getActorUserId(),
                transaction.getOccurredAt(),
                transaction.getCreatedAt(),
                transaction.getVersion(),transaction.getVariant()==null?null:transaction.getVariant().getId());
    }
    public InventoryTransactionResponse(UUID id,UUID balanceId,UUID storeId,UUID productId,InventoryTransactionType transactionType,BigDecimal quantityDelta,BigDecimal resultingQuantity,String referenceType,UUID referenceId,String reason,UUID actorUserId,Instant occurredAt,Instant createdAt,long version){this(id,balanceId,storeId,productId,transactionType,quantityDelta,resultingQuantity,referenceType,referenceId,reason,actorUserId,occurredAt,createdAt,version,null);}
}
