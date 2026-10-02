package com.merchtyl.eod;

import com.merchtyl.sales.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.register.RegisterType;
import com.merchtyl.registersession.RegisterSessionStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request to open a store business day.")
record BusinessDayOpenRequest(
        @Schema(description = "Store identifier.", format = "uuid", example = "0df353c7-6638-4aa8-a700-56861dc0deca")
        @NotNull UUID storeId,
        @Schema(description = "Business date in the store timezone. When omitted, the service derives it from the store timezone.", example = "2026-07-29")
        LocalDate businessDate,
        @Schema(description = "Whether an authorized user is overriding an open previous day.", example = "false")
        boolean overrideOpenPrevious,
        @Schema(description = "Required when overriding a previous open business day.", example = "Prior day was reconciled offline")
        String overrideReason
) {
}

@Schema(description = "Request to close a business day and generate an immutable report.")
record BusinessDayCloseRequest(
        @Schema(description = "Optimistic-lock version of the business day. Stale values return 409 Conflict.", example = "2")
        @NotNull Long version,
        @Schema(description = "Optional manager notes stored with sign-off.", example = "All registers reconciled.")
        String managerNotes,
        @Schema(description = "Required when cash variance exceeds the configured threshold.", example = "Drawer 2 had a documented cash correction.")
        String varianceExplanation,
        @Schema(description = "Authenticated manager confirmation for electronic sign-off.", example = "true")
        @NotNull Boolean confirmationAccepted
) {
}

@Schema(description = "Request to force-close a business day. Requires elevated permission.")
record BusinessDayForceCloseRequest(
        @Schema(description = "Optimistic-lock version of the business day. Stale values return 409 Conflict.", example = "2")
        @NotNull Long version,
        @Schema(description = "Force-close reason stored for audit and report exceptions.", example = "Power outage prevented normal register close.")
        @NotNull String reason,
        String managerNotes,
        String varianceExplanation,
        @NotNull Boolean confirmationAccepted
) {
}

@Schema(description = "Request to reopen a closed business day. The original report is preserved.")
record BusinessDayReopenRequest(
        @Schema(description = "Optimistic-lock version of the business day. Stale values return 409 Conflict.", example = "4")
        @NotNull Long version,
        @Schema(description = "Reopen reason stored for audit.", example = "Late settlement adjustment approved by owner.")
        @NotNull String reason
) {
}

@Schema(description = "Business-day state and audit metadata.")
record BusinessDayResponse(
        @Schema(format = "uuid", example = "de4c0af7-f50e-4575-a4c3-0e5d772fbd01")
        UUID id,
        @Schema(format = "uuid", example = "0df353c7-6638-4aa8-a700-56861dc0deca")
        UUID storeId,
        String storeCode,
        String storeName,
        @Schema(example = "2026-07-29")
        LocalDate businessDate,
        @Schema(description = "IANA timezone used to interpret the business date.", example = "America/New_York")
        String timezone,
        BusinessDayStatus status,
        @Schema(description = "UTC timestamp when the day was opened.", type = "string", format = "date-time", example = "2026-07-29T12:00:00Z")
        Instant openedAt,
        UUID openedBy,
        String openedByName,
        Instant closingStartedAt,
        UUID closingStartedBy,
        String closingStartedByName,
        Instant closedAt,
        UUID closedBy,
        String closedByName,
        Instant reopenedAt,
        UUID reopenedBy,
        String reopenedByName,
        String reopenReason,
        String forceCloseReason,
        @Schema(description = "Optimistic-lock version. Send this value in lifecycle requests.", example = "2")
        long version
) {
    static BusinessDayResponse from(BusinessDay day) {
        return new BusinessDayResponse(
                day.getId(),
                day.getStore().getId(),
                day.getStore().getCode(),
                day.getStore().getName(),
                day.getBusinessDate(),
                day.getTimezone(),
                day.getStatus(),
                day.getOpenedAt(),
                day.getOpenedBy().getId(),
                display(day.getOpenedBy()),
                day.getClosingStartedAt(),
                day.getClosingStartedBy() == null ? null : day.getClosingStartedBy().getId(),
                day.getClosingStartedBy() == null ? null : display(day.getClosingStartedBy()),
                day.getClosedAt(),
                day.getClosedBy() == null ? null : day.getClosedBy().getId(),
                day.getClosedBy() == null ? null : display(day.getClosedBy()),
                day.getReopenedAt(),
                day.getReopenedBy() == null ? null : day.getReopenedBy().getId(),
                day.getReopenedBy() == null ? null : display(day.getReopenedBy()),
                day.getReopenReason(),
                day.getForceCloseReason(),
                day.getVersion());
    }

    private static String display(com.merchtyl.security.User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getEmail() : user.getDisplayName();
    }
}

enum BusinessDayOperationalState {
    NO_BUSINESS_DAY_TODAY,
    OPEN,
    CLOSED_TODAY,
    HISTORICAL_CLOSED,
    PREVIOUS_DAY_STILL_OPEN
}

enum BusinessDayAvailableAction {
    OPEN,
    REOPEN,
    NONE
}

@Schema(description = "Store-local business-day state used to choose today's operational action.")
record BusinessDayOperationalStateResponse(
        UUID storeId,
        LocalDate currentBusinessDate,
        Instant nextBusinessDateAt,
        BusinessDayResponse currentBusinessDay,
        BusinessDayResponse previousBusinessDay,
        BusinessDayOperationalState state,
        BusinessDayAvailableAction availableAction
) {
}

@Schema(description = "Closing readiness response. Includes every blocker found.")
record ClosingValidationResponse(
        UUID businessDayId,
        boolean closable,
        List<ClosingBlockerResponse> blockers,
        List<RegisterReconciliationResponse> registerSessions
) {
    ClosingValidationResponse(UUID businessDayId, boolean closable, List<ClosingBlockerResponse> blockers) {
        this(businessDayId, closable, blockers, List.of());
    }
}

@Schema(description = "Register-session reconciliation state scoped to this Business Day and Store.")
record RegisterReconciliationResponse(
        UUID registerSessionId,
        UUID registerId,
        String registerCode,
        String registerName,
        RegisterType registerType,
        RegisterSessionStatus sessionStatus,
        UUID openedByUserId,
        String openedByName,
        Instant openedAt,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        long version,
        boolean reconciliationRequired,
        boolean reconciliationComplete,
        boolean canReconcile,
        CashLedgerBreakdownResponse reconciliation
) {
}

@Schema(description = "A blocking issue that prevents normal business-day closing.")
record ClosingBlockerResponse(
        @Schema(example = "OPEN_REGISTER_SESSION")
        String code,
        @Schema(example = "Register FRONT-1 still has an open session.")
        String message,
        @Schema(format = "uuid")
        UUID relatedId) {
}

@Schema(description = "Calculated closing preview. This does not persist an EOD report.")
record EndOfDayClosingPreviewResponse(
        UUID businessDayId,
        UUID storeId,
        String storeCode,
        String storeName,
        LocalDate businessDate,
        BusinessDayStatus businessDayStatus,
        long businessDayVersion,
        BigDecimal totalSales,
        BigDecimal totalSalesBeforeTax,
        BigDecimal totalTaxCollected,
        @Schema(description = "Gross completed sales as a decimal monetary value.", example = "1250.00")
        BigDecimal grossSales,
        @Schema(description = "Net sales after discounts, refunds, and voids as a decimal monetary value.", example = "1175.50")
        BigDecimal netSales,
        @Schema(description = "Total discounts as a decimal monetary value.", example = "25.00")
        BigDecimal discountTotal,
        @Schema(description = "Total refunds as a decimal monetary value.", example = "49.50")
        BigDecimal refundTotal,
        @Schema(description = "Total voided sales as a decimal monetary value.", example = "0.00")
        BigDecimal voidTotal,
        @Schema(description = "Total tax as a decimal monetary value.", example = "101.75")
        BigDecimal taxTotal,
        BigDecimal taxableSales,
        BigDecimal nonTaxableSales,
        BigDecimal taxCollected,
        BigDecimal merchandiseNetSales,
        BigDecimal depositsCollected,
        BigDecimal depositPayouts,
        BigDecimal netDeposits,
        long transactionCount,
        BigDecimal averageTransactionValue,
        BigDecimal highestTransactionValue,
        BigDecimal lowestTransactionValue,
        BigDecimal itemsSold,
        BigDecimal averageBasketSize,
        BigDecimal initialOpeningCash,
        BigDecimal cashBeforeFinalSettlement,
        BigDecimal cashRemovedFromTills,
        BigDecimal cashRetainedInTills,
        @Schema(description = "Physical cash retained in tills after final settlement.", example = "440.00")
        BigDecimal expectedCash,
        @Schema(description = "Final counted cash before till settlement, once per physical register.", example = "620.00")
        BigDecimal countedCash,
        @Schema(description = "Final counted cash minus cash expected before settlement, once per physical register.", example = "0.00")
        BigDecimal cashVariance,
        BigDecimal cashVarianceExplanationThreshold,
        boolean varianceExplanationRequired,
        boolean managerSignOffRequired,
        String currencyCode,
        List<EndOfDayRegisterReconciliationResponse> registerReconciliation,
        List<EndOfDayRegisterSummaryResponse> registers,
        List<EndOfDayPaymentSummaryResponse> payments,
        List<EndOfDayTaxSummaryResponse> taxes,
        List<EndOfDayCategorySalesSummaryResponse> categorySalesDistribution,
        EndOfDayLotterySummaryResponse lottery,
        EndOfDayInventorySummaryResponse inventory,
        List<EndOfDayCashierSummaryResponse> cashiers,
        List<EndOfDayExceptionSummaryResponse> exceptions
) {
}

record ClosingReminderResponse(
        UUID storeId,
        UUID businessDayId,
        boolean pastConfiguredClosingTime,
        long openRegisterCount,
        boolean readyForAutomaticReportGeneration,
        String message
) {
}

record EndOfDayReportSearchRequest(
        UUID storeId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BusinessDayStatus status,
        UUID closedBy,
        String reportNumber,
        int page,
        int size
) {
}

@Schema(description = "Immutable End-of-Day report response generated from a persisted snapshot.")
record EndOfDayReportResponse(
        UUID id,
        UUID businessDayId,
        UUID storeId,
        String storeCode,
        String storeName,
        LocalDate businessDate,
        BusinessDayStatus businessDayStatus,
        long businessDayVersion,
        String reportNumber,
        int revision,
        Instant generatedAt,
        UUID generatedBy,
        String generatedByName,
        BigDecimal totalSales,
        BigDecimal totalSalesBeforeTax,
        BigDecimal totalTaxCollected,
        @Schema(description = "Gross completed sales as a decimal monetary value.", example = "1250.00")
        BigDecimal grossSales,
        @Schema(description = "Net sales as a decimal monetary value.", example = "1175.50")
        BigDecimal netSales,
        @Schema(description = "Discount total as a decimal monetary value.", example = "25.00")
        BigDecimal discountTotal,
        @Schema(description = "Refund total as a decimal monetary value.", example = "49.50")
        BigDecimal refundTotal,
        @Schema(description = "Void total as a decimal monetary value.", example = "0.00")
        BigDecimal voidTotal,
        @Schema(description = "Tax total as a decimal monetary value.", example = "101.75")
        BigDecimal taxTotal,
        BigDecimal taxableSales,
        BigDecimal nonTaxableSales,
        BigDecimal taxCollected,
        BigDecimal merchandiseNetSales,
        BigDecimal depositsCollected,
        BigDecimal depositPayouts,
        BigDecimal netDeposits,
        long transactionCount,
        BigDecimal averageTransactionValue,
        BigDecimal highestTransactionValue,
        BigDecimal lowestTransactionValue,
        BigDecimal itemsSold,
        BigDecimal averageBasketSize,
        BigDecimal initialOpeningCash,
        BigDecimal cashBeforeFinalSettlement,
        BigDecimal cashRemovedFromTills,
        BigDecimal cashRetainedInTills,
        @Schema(description = "Physical cash retained in tills after final settlement.", example = "440.00")
        BigDecimal expectedCash,
        @Schema(description = "Final counted cash before till settlement, once per physical register.", example = "620.00")
        BigDecimal countedCash,
        @Schema(description = "Final counted cash minus cash expected before settlement, once per physical register.", example = "0.00")
        BigDecimal cashVariance,
        String currencyCode,
        List<EndOfDayRegisterReconciliationResponse> registerReconciliation,
        List<EndOfDayRegisterSummaryResponse> registers,
        List<EndOfDayPaymentSummaryResponse> payments,
        List<EndOfDayTaxSummaryResponse> taxes,
        List<EndOfDayCategorySalesSummaryResponse> categorySalesDistribution,
        EndOfDayLotterySummaryResponse lottery,
        EndOfDayInventorySummaryResponse inventory,
        List<EndOfDayCashierSummaryResponse> cashiers,
        List<EndOfDayExceptionSummaryResponse> exceptions,
        EndOfDaySignOffResponse signOff,
        String reportSnapshot,
        @Schema(description = "Optimistic-lock version of the report row.", example = "1")
        long version
) {
    static EndOfDayReportResponse from(EndOfDayReport report) {
        return new EndOfDayReportResponse(
                report.getId(),
                report.getBusinessDay().getId(),
                report.getStore().getId(),
                report.getStore().getCode(),
                report.getStore().getName(),
                report.getBusinessDate(),
                report.getBusinessDay().getStatus(),
                report.getBusinessDay().getVersion(),
                report.getReportNumber(),
                report.getRevision(),
                report.getGeneratedAt(),
                report.getGeneratedBy().getId(),
                display(report.getGeneratedBy()),
                totalSales(report),
                totalSalesBeforeTax(report),
                totalTaxCollected(report),
                report.getGrossSales(),
                report.getNetSales(),
                report.getDiscountTotal(),
                report.getRefundTotal(),
                report.getVoidTotal(),
                report.getTaxTotal(),
                report.getTaxSummaries().stream().map(EndOfDayTaxSummary::getTaxableSales).reduce(BigDecimal.ZERO, BigDecimal::add),
                report.getTaxSummaries().stream().map(EndOfDayTaxSummary::getExemptSales).reduce(BigDecimal.ZERO, BigDecimal::add),
                report.getTaxSummaries().stream().map(EndOfDayTaxSummary::getNetTaxCollected).reduce(BigDecimal.ZERO, BigDecimal::add),
                report.getTaxSummaries().stream().map(value -> value.getTaxableSales().add(value.getExemptSales())).reduce(BigDecimal.ZERO, BigDecimal::add),
                report.getDepositsCollected(),
                report.getDepositPayouts(),
                report.getNetDeposits(),
                report.getTransactionCount(),
                report.getAverageTransactionValue(),
                report.getHighestTransactionValue(),
                report.getLowestTransactionValue(),
                report.getItemsSold(),
                report.getAverageBasketSize(),
                initialOpeningCash(report),
                report.getCashBeforeFinalSettlement() == null ? report.getExpectedCash() : report.getCashBeforeFinalSettlement(),
                report.getCashRemovedFromTills() == null ? BigDecimal.ZERO.setScale(2) : report.getCashRemovedFromTills(),
                report.getCashRetainedInTills() == null ? report.getExpectedCash() : report.getCashRetainedInTills(),
                report.getExpectedCash(),
                report.getCountedCash(),
                report.getCashVariance(),
                report.getCurrencyCode(),
                EndOfDayRegisterReconciliationResponse.aggregate(report.getRegisterSummaries().stream().map(EndOfDayRegisterSummaryResponse::from).toList()),
                report.getRegisterSummaries().stream().map(EndOfDayRegisterSummaryResponse::from).toList(),
                report.getPaymentSummaries().stream().map(EndOfDayPaymentSummaryResponse::from).toList(),
                report.getTaxSummaries().stream().map(EndOfDayTaxSummaryResponse::from).toList(),
                report.getCategorySalesSummaries().stream().map(EndOfDayCategorySalesSummaryResponse::from).toList(),
                report.getLotterySummary() == null ? null : EndOfDayLotterySummaryResponse.from(report.getLotterySummary()),
                report.getInventorySummary() == null ? null : EndOfDayInventorySummaryResponse.from(report.getInventorySummary()),
                report.getCashierSummaries().stream().map(EndOfDayCashierSummaryResponse::from).toList(),
                report.getExceptionSummaries().stream().map(EndOfDayExceptionSummaryResponse::from).toList(),
                report.getSignOff() == null ? null : EndOfDaySignOffResponse.from(report.getSignOff()),
                report.getReportSnapshot(),
                report.getVersion());
    }

    private static BigDecimal totalSalesBeforeTax(EndOfDayReport report) {
        return report.getTaxSummaries().stream()
                .map(value -> value.getTaxableSales().add(value.getExemptSales()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal initialOpeningCash(EndOfDayReport report) {
        if (report.getInitialOpeningCash() != null) return report.getInitialOpeningCash();
        return report.getRegisterSummaries().stream()
                .collect(java.util.stream.Collectors.groupingBy(value -> value.getRegister().getId(),
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .values().stream().map(group -> group.stream()
                        .min(java.util.Comparator.comparing(EndOfDayRegisterSummary::getOpenedAt)).orElseThrow().getOpeningFloat())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal totalTaxCollected(EndOfDayReport report) {
        return report.getTaxSummaries().stream()
                .map(EndOfDayTaxSummary::getNetTaxCollected)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal totalSales(EndOfDayReport report) {
        return totalSalesBeforeTax(report).add(totalTaxCollected(report));
    }

    private static String display(com.merchtyl.security.User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getEmail() : user.getDisplayName();
    }
}

record EndOfDayRegisterReconciliationResponse(
        UUID registerId,
        String registerCode,
        String registerName,
        long sessionCount,
        BigDecimal initialFloat,
        BigDecimal targetFloat,
        BigDecimal cashRemoved,
        BigDecimal cashRetained,
        BigDecimal cashBeforeSettlement,
        BigDecimal finalCountedCash,
        BigDecimal variance
) {
    static List<EndOfDayRegisterReconciliationResponse> aggregate(List<EndOfDayRegisterSummaryResponse> sessions) {
        return sessions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        EndOfDayRegisterSummaryResponse::registerId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values().stream()
                .map(group -> {
                    EndOfDayRegisterSummaryResponse latest = group.stream()
                            .max(java.util.Comparator.comparing(EndOfDayRegisterSummaryResponse::closedAt,
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(EndOfDayRegisterSummaryResponse::openedAt))
                            .orElseThrow();
                    return new EndOfDayRegisterReconciliationResponse(
                            latest.registerId(), latest.registerCode(), latest.registerName(),
                            latest.physicalSessionCount() == null ? group.size() : latest.physicalSessionCount(),
                            latest.physicalInitialFloat() == null ? group.get(0).openingFloat() : latest.physicalInitialFloat(),
                            latest.physicalTargetFloat(),
                            latest.physicalCashRemoved() == null ? BigDecimal.ZERO.setScale(2) : latest.physicalCashRemoved(),
                            latest.physicalCashRetained() == null ? latest.expectedCash() : latest.physicalCashRetained(),
                            latest.expectedCash(), latest.countedCash(), latest.variance());
                })
                .sorted(java.util.Comparator.comparing(EndOfDayRegisterReconciliationResponse::registerCode))
                .toList();
    }
}

record EndOfDayCategorySalesSummaryResponse(
        UUID categoryId,
        String categoryCode,
        String categoryName,
        String taxTreatment,
        String taxTreatmentLabel,
        String taxCategoryCode,
        String taxCategoryName,
        BigDecimal quantitySold,
        BigDecimal grossSales,
        BigDecimal discounts,
        BigDecimal refunds,
        BigDecimal netSales,
        BigDecimal taxCollected,
        BigDecimal percentage
) {
    static EndOfDayCategorySalesSummaryResponse from(EndOfDayCategorySalesSummary summary) {
        return new EndOfDayCategorySalesSummaryResponse(
                summary.getCategoryId(),
                summary.getCategoryCode(),
                summary.getCategoryName(),
                summary.getTaxTreatment(),
                summary.getTaxTreatmentLabel(),
                summary.getTaxCategoryCode(),
                summary.getTaxCategoryName(),
                summary.getQuantitySold(),
                summary.getGrossSales(),
                summary.getDiscounts(),
                summary.getRefunds(),
                summary.getNetSales(),
                summary.getTaxCollected(),
                summary.getPercentage());
    }
}

record EndOfDayRegisterSummaryResponse(
        UUID registerSessionId,
        UUID registerId,
        String registerCode,
        String registerName,
        BigDecimal openingFloat,
        BigDecimal cashReceipts,
        BigDecimal changeGiven,
        BigDecimal cashRefunds,
        BigDecimal lotteryCashSales,
        BigDecimal lotteryPayouts,
        BigDecimal lotteryPayoutReversals,
        BigDecimal lotterySaleCancellations,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal safeDrops,
        BigDecimal floatAdditions,
        BigDecimal floatRemovals,
        BigDecimal expenses,
        BigDecimal payouts,
        BigDecimal payoutReversals,
        BigDecimal closingAdjustments,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        BigDecimal physicalInitialFloat,
        BigDecimal physicalTargetFloat,
        BigDecimal physicalCashRemoved,
        BigDecimal physicalCashRetained,
        Integer physicalSessionCount,
        BigDecimal sessionTargetFloat,
        BigDecimal sessionCashRemoved,
        BigDecimal sessionCashRetained,
        UUID openedBy,
        String openedByName,
        UUID closedBy,
        String closedByName,
        Instant openedAt,
        Instant closedAt,
        boolean forceClosed,
        String forceCloseReason
) {
    static EndOfDayRegisterSummaryResponse from(EndOfDayRegisterSummary summary) {
        return new EndOfDayRegisterSummaryResponse(
                summary.getRegisterSession() == null ? null : summary.getRegisterSession().getId(),
                summary.getRegister().getId(),
                summary.getRegisterCode(),
                summary.getRegisterName(),
                summary.getOpeningFloat(),
                summary.getCashReceipts(),
                summary.getChangeGiven(),
                summary.getCashRefunds(),
                summary.getLotteryCashSales(),
                summary.getLotteryPayouts(),
                summary.getLotteryPayoutReversals(),
                summary.getLotterySaleCancellations(),
                summary.getCashIn(),
                summary.getCashOut(),
                summary.getSafeDrops(),
                summary.getFloatAdditions(),
                summary.getFloatRemovals(),
                summary.getExpenses(),
                summary.getPayouts(),
                summary.getPayoutReversals(),
                summary.getClosingAdjustments(),
                summary.getExpectedCash(),
                summary.getCountedCash(),
                summary.getVariance(),
                summary.getPhysicalInitialFloat(),
                summary.getPhysicalTargetFloat(),
                summary.getPhysicalCashRemoved(),
                summary.getPhysicalCashRetained(),
                summary.getPhysicalSessionCount(),
                summary.getSessionTargetFloat(),
                summary.getSessionCashRemoved(),
                summary.getSessionCashRetained(),
                summary.getOpenedBy().getId(),
                summary.getOpenedByName(),
                summary.getClosedBy() == null ? null : summary.getClosedBy().getId(),
                summary.getClosedByName(),
                summary.getOpenedAt(),
                summary.getClosedAt(),
                summary.isForceClosed(),
                summary.getForceCloseReason());
    }
}

record EndOfDayPaymentSummaryResponse(
        PaymentMethod paymentMethod,
        BigDecimal collected,
        BigDecimal refunded,
        BigDecimal net,
        BigDecimal cashTendered,
        BigDecimal changeGiven,
        long transactionCount,
        long splitPaymentCount
) {
    static EndOfDayPaymentSummaryResponse from(EndOfDayPaymentSummary summary) {
        return new EndOfDayPaymentSummaryResponse(
                summary.getPaymentMethod(),
                summary.getCollected(),
                summary.getRefunded(),
                summary.getNet(),
                summary.getCashTendered(),
                summary.getChangeGiven(),
                summary.getTransactionCount(),
                summary.getSplitPaymentCount());
    }
}

record EndOfDayTaxSummaryResponse(
        String componentCode,
        String componentName,
        BigDecimal taxableSales,
        BigDecimal exemptSales,
        BigDecimal zeroRatedSales,
        BigDecimal outOfScopeSales,
        BigDecimal taxCollected,
        BigDecimal taxRefunded,
        BigDecimal roundingAdjustment,
        BigDecimal netTaxCollected
) {
    static EndOfDayTaxSummaryResponse from(EndOfDayTaxSummary summary) {
        return new EndOfDayTaxSummaryResponse(
                summary.getComponentCode(),
                summary.getComponentName(),
                summary.getTaxableSales(),
                summary.getExemptSales(),
                summary.getZeroRatedSales(),
                summary.getOutOfScopeSales(),
                summary.getTaxCollected(),
                summary.getTaxRefunded(),
                summary.getRoundingAdjustment(),
                summary.getNetTaxCollected());
    }
}

record EndOfDayLotterySummaryResponse(
        boolean enabled,
        BigDecimal lotterySales,
        BigDecimal lotteryPayouts,
        BigDecimal netLottery,
        BigDecimal saleCancellations,
        BigDecimal payoutReversals,
        BigDecimal cashLotteryActivity,
        BigDecimal nonCashLotteryActivity,
        BigDecimal commissionEarned,
        BigDecimal settlementAmount,
        long operatorReferrals,
        long pendingReferrals,
        long approvalCount,
        long rejectedPayouts,
        String operatorTotals,
        String registerTotals,
        String cashierTotals
) {
    static EndOfDayLotterySummaryResponse from(EndOfDayLotterySummary summary) {
        return new EndOfDayLotterySummaryResponse(
                summary.isEnabled(),
                summary.getLotterySales(),
                summary.getLotteryPayouts(),
                summary.getNetLottery(),
                summary.getSaleCancellations(),
                summary.getPayoutReversals(),
                summary.getCashLotteryActivity(),
                summary.getNonCashLotteryActivity(),
                summary.getCommissionEarned(),
                summary.getSettlementAmount(),
                summary.getOperatorReferrals(),
                summary.getPendingReferrals(),
                summary.getApprovalCount(),
                summary.getRejectedPayouts(),
                summary.getOperatorTotals(),
                summary.getRegisterTotals(),
                summary.getCashierTotals());
    }
}

record EndOfDayInventorySummaryResponse(
        BigDecimal deductedBySales,
        BigDecimal restoredByReturns,
        BigDecimal manualIncreases,
        BigDecimal manualDecreases,
        BigDecimal damagedQuantity,
        BigDecimal expiredQuantity,
        BigDecimal transferIn,
        BigDecimal transferOut,
        BigDecimal stockCountVariances,
        long lowStockProducts,
        long negativeStockProducts,
        BigDecimal inventoryValueMovement
) {
    static EndOfDayInventorySummaryResponse from(EndOfDayInventorySummary summary) {
        return new EndOfDayInventorySummaryResponse(
                summary.getDeductedBySales(),
                summary.getRestoredByReturns(),
                summary.getManualIncreases(),
                summary.getManualDecreases(),
                summary.getDamagedQuantity(),
                summary.getExpiredQuantity(),
                summary.getTransferIn(),
                summary.getTransferOut(),
                summary.getStockCountVariances(),
                summary.getLowStockProducts(),
                summary.getNegativeStockProducts(),
                summary.getInventoryValueMovement());
    }
}

record EndOfDayCashierSummaryResponse(
        UUID cashierId,
        String cashierName,
        long transactionCount,
        BigDecimal grossSales,
        BigDecimal netSales,
        BigDecimal refundTotal,
        long voidCount,
        BigDecimal discountTotal,
        long priceOverrideCount,
        BigDecimal cashHandled,
        BigDecimal lotterySales,
        BigDecimal lotteryPayouts,
        BigDecimal averageTransactionValue,
        Instant firstActivityAt,
        Instant lastActivityAt,
        String registersUsed
) {
    static EndOfDayCashierSummaryResponse from(EndOfDayCashierSummary summary) {
        return new EndOfDayCashierSummaryResponse(
                summary.getCashier().getId(),
                summary.getCashierName(),
                summary.getTransactionCount(),
                summary.getGrossSales(),
                summary.getNetSales(),
                summary.getRefundTotal(),
                summary.getVoidCount(),
                summary.getDiscountTotal(),
                summary.getPriceOverrideCount(),
                summary.getCashHandled(),
                summary.getLotterySales(),
                summary.getLotteryPayouts(),
                summary.getAverageTransactionValue(),
                summary.getFirstActivityAt(),
                summary.getLastActivityAt(),
                summary.getRegistersUsed());
    }
}

record EndOfDayExceptionSummaryResponse(
        EndOfDayExceptionType exceptionType,
        long count,
        BigDecimal totalAmount,
        String details
) {
    static EndOfDayExceptionSummaryResponse from(EndOfDayExceptionSummary summary) {
        return new EndOfDayExceptionSummaryResponse(
                summary.getExceptionType(),
                summary.getCount(),
                summary.getTotalAmount(),
                summary.getDetails());
    }
}

record EndOfDaySignOffResponse(
        UUID managerUserId,
        String managerName,
        Instant signedAt,
        String notes,
        String varianceExplanation,
        boolean confirmationAccepted
) {
    static EndOfDaySignOffResponse from(EndOfDaySignOff signOff) {
        return new EndOfDaySignOffResponse(
                signOff.getManager().getId(),
                signOff.getManager().getDisplayName(),
                signOff.getSignedAt(),
                signOff.getNotes(),
                signOff.getVarianceExplanation(),
                signOff.isConfirmationAccepted());
    }
}
