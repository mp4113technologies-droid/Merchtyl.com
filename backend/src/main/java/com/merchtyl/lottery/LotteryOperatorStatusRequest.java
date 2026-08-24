package com.merchtyl.lottery;

import jakarta.validation.constraints.NotNull;

public record LotteryOperatorStatusRequest(
        boolean active,
        @NotNull Long version
) {
}
