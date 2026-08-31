package com.merchtyl.eod;

import com.merchtyl.sales.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Schema(description = "Closing readiness response. Includes every blocker found.")
record ClosingValidationResponse(
        UUID businessDayId,
        boolean closable,
        List<ClosingBlockerResponse> blockers
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
        long transactionCount,
        BigDecimal averageTransactionValue,
        BigDecimal highestTransactionValue,
        BigDecimal lowestTransactionValue,
        BigDecimal itemsSold,
        BigDecimal averageBasketSize,
        @Schema(description = "Expected cash as a decimal monetary value.", example = "620.00")
        BigDecimal expectedCash,
        @Schema(description = "Counted cash as a decimal monetary value.", example = "620.00")
        BigDecimal countedCash,
        @Schema(description = "Counted cash minus expected cash as a decimal monetary value.", example = "0.00")
        BigDecimal cashVariance,
        BigDecimal cashVarianceExplanationThreshold,
        boolean varianceExplanationRequired,
        boolean managerSignOffRequired,
        String currencyCode,
        List<EndOfDayRegisterSummaryResponse> registers,
        List<EndOfDayPaymentSummaryResponse> payments,
        List<EndOfDayTaxSummaryResponse> taxes,
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
        long transactionCount,
        BigDecimal averageTransactionValue,
        BigDecimal highestTransactionValue,
        BigDecimal lowestTransactionValue,
        BigDecimal itemsSold,
        BigDecimal averageBasketSize,
        @Schema(description = "Expected cash as a decimal monetary value.", example = "620.00")
        BigDecimal expectedCash,
        @Schema(description = "Counted cash as a decimal monetary value.", example = "620.00")
        BigDecimal countedCash,
        @Schema(description = "Cash variance as a decimal monetary value.", example = "0.00")
        BigDecimal cashVariance,
        String currencyCode,
        List<EndOfDayRegisterSummaryResponse> registers,
        List<EndOfDayPaymentSummaryResponse> payments,
        List<EndOfDayTaxSummaryResponse> taxes,
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
                report.getGrossSales(),
                report.getNetSales(),
                report.getDiscountTotal(),
                report.getRefundTotal(),
                report.getVoidTotal(),
                report.getTaxTotal(),
                report.getTransactionCount(),
                report.getAverageTransactionValue(),
                report.getHighestTransactionValue(),
                report.getLowestTransactionValue(),
                report.getItemsSold(),
                report.getAverageBasketSize(),
                report.getExpectedCash(),
                report.getCountedCash(),
                report.getCashVariance(),
                report.getCurrencyCode(),
                report.getRegisterSummaries().stream().map(EndOfDayRegisterSummaryResponse::from).toList(),
                report.getPaymentSummaries().stream().map(EndOfDayPaymentSummaryResponse::from).toList(),
                report.getTaxSummaries().stream().map(EndOfDayTaxSummaryResponse::from).toList(),
                report.getLotterySummary() == null ? null : EndOfDayLotterySummaryResponse.from(report.getLotterySummary()),
                report.getInventorySummary() == null ? null : EndOfDayInventorySummaryResponse.from(report.getInventorySummary()),
                report.getCashierSummaries().stream().map(EndOfDayCashierSummaryResponse::from).toList(),
                report.getExceptionSummaries().stream().map(EndOfDayExceptionSummaryResponse::from).toList(),
                report.getSignOff() == null ? null : EndOfDaySignOffResponse.from(report.getSignOff()),
                report.getReportSnapshot(),
                report.getVersion());
    }

    private static String display(com.merchtyl.security.User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getEmail() : user.getDisplayName();
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
        BigDecimal closingAdjustments,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
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
                summary.getClosingAdjustments(),
                summary.getExpectedCash(),
                summary.getCountedCash(),
                summary.getVariance(),
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
