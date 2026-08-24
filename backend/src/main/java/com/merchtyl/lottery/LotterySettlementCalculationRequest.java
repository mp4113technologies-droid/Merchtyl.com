package com.merchtyl.lottery;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record LotterySettlementCalculationRequest(
        @NotNull UUID operatorId,
        @NotNull UUID storeId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {
}
