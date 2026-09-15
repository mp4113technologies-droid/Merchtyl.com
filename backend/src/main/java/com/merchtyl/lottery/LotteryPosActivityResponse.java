package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotteryPosActivityResponse(UUID id, LotteryPosActivityType type, String source, BigDecimal amount,
                                         String currencyCode, UUID storeId, UUID registerId,
                                         UUID registerSessionId, Instant occurredAt) {
    static LotteryPosActivityResponse from(LotteryPosActivity activity) {
        return new LotteryPosActivityResponse(activity.getId(), activity.getType(), activity.getSource(),
                activity.getAmount(), activity.getCurrencyCode(), activity.getStore().getId(),
                activity.getRegister().getId(), activity.getRegisterSession().getId(), activity.getOccurredAt());
    }
}
