package com.merchtyl.lottery;

import com.merchtyl.sales.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotterySaleRequest(
        @NotNull UUID operatorId,
        @Size(max = 180) String operatorReference,
        @Size(max = 180) String ticketReference,
        @NotNull LotteryGameType gameType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        @NotNull UUID storeId,
        @NotNull UUID registerId,
        @NotNull UUID deviceId,
        UUID registerSessionId,
        Instant occurredAt
) {
}
