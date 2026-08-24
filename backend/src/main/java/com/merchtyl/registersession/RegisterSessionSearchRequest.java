package com.merchtyl.registersession;

import java.time.Instant;
import java.util.UUID;

public record RegisterSessionSearchRequest(
        UUID storeId,
        UUID registerId,
        UUID deviceId,
        UUID assignedCashierId,
        RegisterSessionStatus status,
        Instant openedFrom,
        Instant openedTo,
        int page,
        int size
) {
}
