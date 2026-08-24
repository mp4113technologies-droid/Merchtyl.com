package com.merchtyl.lottery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotteryPayoutCreateRequest(
        @NotNull UUID operatorId,
        @NotNull UUID storeId,
        @NotNull UUID registerId,
        @NotNull UUID deviceId,
        UUID registerSessionId,
        @NotBlank @Size(max = 180) String ticketNumber,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LotteryPayoutMethod payoutMethod,
        LocalDate businessDate,
        Instant occurredAt,
        @Size(max = 1000) String notes
) {
}
