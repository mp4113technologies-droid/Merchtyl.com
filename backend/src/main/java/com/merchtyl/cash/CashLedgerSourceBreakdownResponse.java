package com.merchtyl.cash;

import java.math.BigDecimal;

public record CashLedgerSourceBreakdownResponse(
        CashLedgerSourceType sourceType,
        CashLedgerDirection direction,
        BigDecimal amount
) {
}
