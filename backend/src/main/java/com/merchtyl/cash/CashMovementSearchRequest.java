package com.merchtyl.cash;

import java.time.Instant;
import java.util.UUID;

public record CashMovementSearchRequest(
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        CashMovementType type,
        Instant occurredFrom,
        Instant occurredTo,
        int page,
        int size
) {
}
