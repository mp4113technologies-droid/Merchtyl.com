package com.merchtyl.lottery;

import java.util.UUID;

public record LotteryPayoutPolicySearchRequest(
        UUID operatorId,
        UUID jurisdictionId,
        UUID storeId,
        LotteryPayoutPolicyStatus status,
        Integer page,
        Integer size
) {
}
