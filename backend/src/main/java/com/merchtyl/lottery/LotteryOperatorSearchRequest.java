package com.merchtyl.lottery;

import java.util.UUID;

public record LotteryOperatorSearchRequest(
        String code,
        String name,
        UUID jurisdictionId,
        SettlementFrequency settlementFrequency,
        Boolean active,
        int page,
        int size
) {
}
