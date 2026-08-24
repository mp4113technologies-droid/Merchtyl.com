package com.merchtyl.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockCountSearchRequest(
        UUID storeId,
        StockCountStatus status,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size
) {
}
