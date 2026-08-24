package com.merchtyl.lottery;

import jakarta.validation.constraints.NotNull;

public record LotteryPayoutPolicyStatusRequest(
        @NotNull LotteryPayoutPolicyStatus status,
        @NotNull Long version
) {
}
