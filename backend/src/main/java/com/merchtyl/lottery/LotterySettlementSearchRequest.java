package com.merchtyl.lottery;

import java.time.LocalDate;
import java.util.UUID;

public record LotterySettlementSearchRequest(
        UUID operatorId,
        UUID storeId,
        LotterySettlementStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer page,
        Integer size
) {
}
