package com.merchtyl.refunds;

import java.util.UUID;

public record RefundSearchRequest(
        UUID originalSaleId,
        UUID returnId,
        UUID storeId,
        UUID registerSessionId,
        int page,
        int size
) {
}
