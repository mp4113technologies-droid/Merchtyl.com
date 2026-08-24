package com.merchtyl.sales;

import java.util.UUID;

public record SaleSearchRequest(
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        UUID createdBy,
        SaleStatus status,
        int page,
        int size
) {
}
