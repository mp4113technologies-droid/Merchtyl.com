package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotterySaleCancellationResponse(
        UUID id,
        UUID originalSaleId,
        UUID cancelledBy,
        String cancelledByEmail,
        String cancelledByDisplayName,
        BigDecimal amount,
        String currencyCode,
        boolean cashReturned,
        UUID operationId,
        Instant cancelledAt,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static LotterySaleCancellationResponse from(LotterySaleCancellation cancellation) {
        return new LotterySaleCancellationResponse(
                cancellation.getId(),
                cancellation.getOriginalSale().getId(),
                cancellation.getCancelledBy().getId(),
                cancellation.getCancelledBy().getEmail(),
                cancellation.getCancelledBy().getDisplayName(),
                cancellation.getAmount(),
                cancellation.getCurrencyCode(),
                cancellation.isCashReturned(),
                cancellation.getOperationId(),
                cancellation.getCancelledAt(),
                cancellation.getReason(),
                cancellation.getCreatedAt(),
                cancellation.getUpdatedAt(),
                cancellation.getVersion());
    }
}
