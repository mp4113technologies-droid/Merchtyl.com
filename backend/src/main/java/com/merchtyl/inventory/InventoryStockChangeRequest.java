package com.merchtyl.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryStockChangeRequest(
        @NotNull UUID storeId,
        @NotNull UUID productId,
        @NotNull InventoryTransactionType transactionType,
        @NotNull BigDecimal quantityDelta,
        @Size(max = 80) String referenceType,
        UUID referenceId,
        @Size(max = 1000) String reason,
        Instant occurredAt,
        Long balanceVersion,
        UUID variantId
) {
    public InventoryStockChangeRequest(UUID storeId,UUID productId,InventoryTransactionType transactionType,
                                       BigDecimal quantityDelta,String referenceType,UUID referenceId,String reason,
                                       Instant occurredAt,Long balanceVersion){
        this(storeId,productId,transactionType,quantityDelta,referenceType,referenceId,reason,occurredAt,balanceVersion,null);
    }
}
