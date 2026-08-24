package com.merchtyl.lottery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LotteryPayoutAuthorizationRequest(
        @NotNull Long version,
        @Size(max = 1000) String approvalNotes
) {
}
