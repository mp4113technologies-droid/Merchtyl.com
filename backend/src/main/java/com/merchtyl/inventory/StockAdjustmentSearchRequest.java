package com.merchtyl.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockAdjustmentSearchRequest(
        UUID storeId,
        StockAdjustmentApprovalStatus approvalStatus,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size
) {
}
