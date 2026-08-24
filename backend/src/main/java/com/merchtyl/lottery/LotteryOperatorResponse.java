package com.merchtyl.lottery;

import java.time.Instant;
import java.util.UUID;

public record LotteryOperatorResponse(
        UUID id,
        String code,
        String name,
        UUID jurisdictionId,
        String jurisdictionCode,
        String jurisdictionName,
        String supportContact,
        SettlementFrequency settlementFrequency,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static LotteryOperatorResponse from(LotteryOperator operator) {
        return new LotteryOperatorResponse(
                operator.getId(),
                operator.getCode(),
                operator.getName(),
                operator.getJurisdiction().getId(),
                operator.getJurisdiction().getCode(),
                operator.getJurisdiction().getName(),
                operator.getSupportContact(),
                operator.getSettlementFrequency(),
                operator.isActive(),
                operator.getCreatedAt(),
                operator.getUpdatedAt(),
                operator.getVersion());
    }
}
