package com.merchtyl.audit;

import java.util.UUID;

public record CreateAuditRecordCommand(
        UUID actorUserId,
        AuditAction action,
        String entityType,
        UUID entityId,
        UUID storeId,
        UUID registerId,
        Object beforeSnapshot,
        Object afterSnapshot,
        String reason
) {
}
