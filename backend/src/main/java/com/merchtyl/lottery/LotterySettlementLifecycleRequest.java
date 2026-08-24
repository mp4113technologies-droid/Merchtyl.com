package com.merchtyl.lottery;

import jakarta.validation.constraints.NotNull;

public record LotterySettlementLifecycleRequest(
        @NotNull Long version,
        String reason,
        String notes
) {
}
