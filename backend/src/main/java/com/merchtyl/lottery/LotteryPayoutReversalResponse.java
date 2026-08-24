package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotteryPayoutReversalResponse(
        UUID id,
        UUID originalPayoutId,
        UUID reversedBy,
        String reversedByEmail,
        String reversedByDisplayName,
        BigDecimal amount,
        String currencyCode,
        UUID operationId,
        Instant reversedAt,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static LotteryPayoutReversalResponse from(LotteryPayoutReversal reversal) {
        return new LotteryPayoutReversalResponse(
                reversal.getId(),
                reversal.getOriginalPayout().getId(),
                reversal.getReversedBy().getId(),
                reversal.getReversedBy().getEmail(),
                reversal.getReversedBy().getDisplayName(),
                reversal.getAmount(),
                reversal.getCurrencyCode(),
                reversal.getOperationId(),
                reversal.getReversedAt(),
                reversal.getReason(),
                reversal.getCreatedAt(),
                reversal.getUpdatedAt(),
                reversal.getVersion());
    }
}
