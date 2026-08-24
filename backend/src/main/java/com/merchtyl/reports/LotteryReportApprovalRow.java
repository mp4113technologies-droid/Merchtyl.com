package com.merchtyl.reports;

import com.merchtyl.lottery.LotteryPayoutApprovalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotteryReportApprovalRow(
        UUID id,
        UUID payoutId,
        String ticketNumber,
        UUID operatorId,
        String operatorCode,
        String operatorName,
        UUID storeId,
        String storeCode,
        String storeName,
        UUID registerId,
        String registerCode,
        String registerName,
        UUID cashierId,
        String cashierEmail,
        String cashierDisplayName,
        LotteryPayoutApprovalType approvalType,
        UUID approvedBy,
        String approvedByEmail,
        String approvedByDisplayName,
        Instant approvedAt,
        BigDecimal payoutAmount,
        BigDecimal thresholdAmount,
        String notes
) {
}
