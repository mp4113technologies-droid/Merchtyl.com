package com.merchtyl.lottery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LotteryOperatorRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull UUID jurisdictionId,
        @Size(max = 1000) String supportContact,
        @NotNull SettlementFrequency settlementFrequency,
        boolean active
) {
}
