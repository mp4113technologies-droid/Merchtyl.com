package com.merchtyl.lottery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LotteryAdjustmentRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
