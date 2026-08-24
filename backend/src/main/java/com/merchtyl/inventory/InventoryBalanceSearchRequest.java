package com.merchtyl.inventory;

import java.util.UUID;

public record InventoryBalanceSearchRequest(
        UUID storeId,
        UUID productId,
        int page,
        int size
) {
}
