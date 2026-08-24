package com.merchtyl.reports;

import com.merchtyl.lottery.LotteryPayoutResponse;
import com.merchtyl.lottery.LotteryPayoutReversalResponse;
import com.merchtyl.lottery.LotterySaleCancellationResponse;
import com.merchtyl.lottery.LotterySaleResponse;
import com.merchtyl.lottery.LotterySettlementResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LotteryReportResponse(
        UUID operatorId,
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal sales,
        long saleCount,
        BigDecimal payouts,
        long payoutCount,
        BigDecimal approvals,
        long approvalCount,
        BigDecimal reversals,
        long reversalCount,
        BigDecimal referrals,
        long referralCount,
        BigDecimal cancellations,
        long cancellationCount,
        BigDecimal commission,
        BigDecimal calculatedSettlement,
        BigDecimal settlement,
        BigDecimal variance,
        List<LotterySaleResponse> saleRows,
        List<LotteryPayoutResponse> payoutRows,
        List<LotteryReportApprovalRow> approvalRows,
        List<LotteryPayoutReversalResponse> reversalRows,
        List<LotteryPayoutResponse> referralRows,
        List<LotterySaleCancellationResponse> cancellationRows,
        List<LotteryReportCommissionRow> commissionRows,
        List<LotterySettlementResponse> settlementRows,
        List<LotteryReportChartPoint> chartRows,
        Instant generatedAt
) {
}
