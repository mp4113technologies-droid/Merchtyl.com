package com.merchtyl.cash;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(
        UUID id,
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        CashMovementType type,
        CashLedgerDirection direction,
        BigDecimal amount,
        String currencyCode,
        String reason,
        String notes,
        UUID createdBy,
        Instant occurredAt,
        UUID approvedBy,
        Instant approvedAt,
        String approvalNotes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static CashMovementResponse from(CashMovement movement) {
        return new CashMovementResponse(
                movement.getId(),
                movement.getStore().getId(),
                movement.getRegister().getId(),
                movement.getRegisterSession().getId(),
                movement.getType(),
                movement.getDirection(),
                movement.getAmount(),
                movement.getCurrencyCode(),
                movement.getReason(),
                movement.getNotes(),
                movement.getCreatedBy().getId(),
                movement.getOccurredAt(),
                movement.getApprovedBy() == null ? null : movement.getApprovedBy().getId(),
                movement.getApprovedAt(),
                movement.getApprovalNotes(),
                movement.getCreatedAt(),
                movement.getUpdatedAt(),
                movement.getVersion());
    }
}
