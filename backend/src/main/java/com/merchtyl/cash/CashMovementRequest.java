package com.merchtyl.cash;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementRequest(
        @NotNull UUID registerSessionId,
        @NotNull CashMovementType type,
        CashLedgerDirection direction,
        @NotNull BigDecimal amount,
        @NotNull String reason,
        String notes,
        @NotNull Instant occurredAt,
        String approvalNotes
) {
}
