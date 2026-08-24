package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotteryCommissionRuleResponse(
        UUID id,
        String name,
        UUID operatorId,
        String operatorCode,
        String operatorName,
        UUID jurisdictionId,
        String jurisdictionCode,
        String jurisdictionName,
        UUID storeId,
        String storeCode,
        String storeName,
        LotteryCommissionRuleType ruleType,
        BigDecimal commissionRatePercent,
        BigDecimal fixedAmount,
        String currencyCode,
        LotteryCommissionPeriod fixedPeriod,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LotteryCommissionRuleStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static LotteryCommissionRuleResponse from(LotteryCommissionRule rule) {
        return new LotteryCommissionRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getOperator().getId(),
                rule.getOperator().getCode(),
                rule.getOperator().getName(),
                rule.getJurisdiction().getId(),
                rule.getJurisdiction().getCode(),
                rule.getJurisdiction().getName(),
                rule.getStore().getId(),
                rule.getStore().getCode(),
                rule.getStore().getName(),
                rule.getRuleType(),
                rule.getCommissionRatePercent(),
                rule.getFixedAmount(),
                rule.getCurrencyCode(),
                rule.getFixedPeriod(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.getStatus(),
                rule.getNotes(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                rule.getVersion());
    }
}
