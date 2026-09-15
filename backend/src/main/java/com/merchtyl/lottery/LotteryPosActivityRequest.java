package com.merchtyl.lottery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record LotteryPosActivityRequest(
        @NotNull UUID registerSessionId,
        @NotNull LotteryPosActivityType type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull UUID operationId) {}
