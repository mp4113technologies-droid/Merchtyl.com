package com.merchtyl.lottery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LotteryCommissionRuleRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID operatorId,
        @NotNull UUID jurisdictionId,
        @NotNull UUID storeId,
        @NotNull LotteryCommissionRuleType ruleType,
        BigDecimal commissionRatePercent,
        BigDecimal fixedAmount,
        String currencyCode,
        LotteryCommissionPeriod fixedPeriod,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotNull LotteryCommissionRuleStatus status,
        @Size(max = 500) String notes
) {
}
