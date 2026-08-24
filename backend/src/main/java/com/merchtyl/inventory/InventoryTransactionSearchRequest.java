package com.merchtyl.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryTransactionSearchRequest(
        UUID storeId,
        UUID productId,
        InventoryTransactionType transactionType,
        UUID referenceId,
        Instant occurredFrom,
        Instant occurredTo,
        int page,
        int size
) {
}
