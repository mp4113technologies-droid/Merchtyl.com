package com.merchtyl.registersession;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterTillSettlementResponse(
        UUID registerSessionId,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        BigDecimal targetTillFloat,
        BigDecimal cashToLeave,
        BigDecimal cashToRemove,
        BigDecimal amountNeededToRestoreFloat,
        boolean override
) {}
