package com.merchtyl.lottery;

import java.util.UUID;

public record LotteryPayoutSearchRequest(
        UUID operatorId,
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        LotteryPayoutStatus status,
        int page,
        int size
) {
}
