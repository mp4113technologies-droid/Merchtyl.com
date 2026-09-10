package com.merchtyl.payments;

import java.math.BigDecimal;

public record CashRoundingResult(
        BigDecimal originalAmount,
        BigDecimal roundedAmount,
        BigDecimal adjustment
) {
}
