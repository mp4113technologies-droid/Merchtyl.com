package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.util.UUID;

public record LotteryPayoutCashAvailabilityResponse(
        UUID registerSessionId,
        UUID policyId,
        BigDecimal expectedDrawerCash,
        BigDecimal protectedRegisterFloat,
        BigDecimal reservedObligations,
        BigDecimal availablePayoutCash,
        String currencyCode
) {
}
