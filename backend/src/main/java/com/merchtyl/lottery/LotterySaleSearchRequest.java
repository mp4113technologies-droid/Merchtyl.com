package com.merchtyl.lottery;

import com.merchtyl.sales.PaymentMethod;

import java.time.Instant;
import java.util.UUID;

public record LotterySaleSearchRequest(
        String search,
        UUID operatorId,
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        UUID registerSessionId,
        LotteryGameType gameType,
        LotterySaleStatus status,
        PaymentMethod paymentMethod,
        Instant occurredFrom,
        Instant occurredTo,
        int page,
        int size
) {
}
