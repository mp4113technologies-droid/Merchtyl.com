package com.merchtyl.reports;

import com.merchtyl.lottery.LotterySettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LotteryReportCommissionRow(
        UUID settlementId,
        UUID operatorId,
        String operatorCode,
        String operatorName,
        UUID storeId,
        String storeCode,
        String storeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal grossSales,
        BigDecimal totalPayouts,
        BigDecimal commission,
        BigDecimal expectedSettlement,
        LotterySettlementStatus status
) {
}
