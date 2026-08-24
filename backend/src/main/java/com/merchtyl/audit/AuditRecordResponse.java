package com.merchtyl.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditRecordResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        UUID storeId,
        UUID registerId,
        String beforeSnapshot,
        String afterSnapshot,
        String reason,
        String correlationId,
        Instant createdAt
) {
    static AuditRecordResponse from(AuditRecord record) {
        return new AuditRecordResponse(
                record.getId(),
                record.getActorUserId(),
                record.getAction(),
                record.getEntityType(),
                record.getEntityId(),
                record.getStoreId(),
                record.getRegisterId(),
                record.getBeforeSnapshot(),
                record.getAfterSnapshot(),
                record.getReason(),
                record.getCorrelationId(),
                record.getCreatedAt());
    }
}
