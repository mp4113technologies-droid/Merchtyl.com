package com.merchtyl.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditSearchRequest(
        String action,
        String entityType,
        UUID entityId,
        UUID actorUserId,
        UUID storeId,
        UUID registerId,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size
) {
}
