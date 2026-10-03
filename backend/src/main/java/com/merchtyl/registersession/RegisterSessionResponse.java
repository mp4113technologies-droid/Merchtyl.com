package com.merchtyl.registersession;

import java.math.BigDecimal;
import java.time.Instant;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import java.util.UUID;
import com.merchtyl.register.RegisterType;
import com.merchtyl.sales.SalesClassification;

public record RegisterSessionResponse(
        UUID id,
        UUID storeId,
        UUID registerId,
        RegisterType registerType,
        UUID deviceId,
        String deviceName,
        UUID assignedCashierId,
        String assignedCashierEmail,
        String assignedCashierDisplayName,
        UUID openedByUserId,
        String openedByDisplayName,
        RegisterSessionStatus status,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal expectedCashAtClose,
        BigDecimal differenceCash,
        UUID closedByUserId,
        String closedByEmail,
        String closedByDisplayName,
        Instant closedAt,
        String forceCloseReason,
        String varianceExplanation,
        CashLedgerBreakdownResponse reconciliation,
        Instant openedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        boolean tillSecured,
        Instant tillSecuredAt,
        Instant tillPinLockedUntil,
        boolean posPinConfigured,
        SalesClassification salesClassification,
        BigDecimal targetFloatAtClose,
        BigDecimal cashRetained,
        BigDecimal cashRemoved,
        UUID retentionOverrideByUserId,
        String retentionOverrideByDisplayName,
        String retentionOverrideReason,
        String currencyCode,
        RegisterSessionOpeningSource openingSource
) {
    public RegisterSessionResponse(
            UUID id, UUID storeId, UUID registerId, UUID deviceId,
            UUID assignedCashierId, String assignedCashierEmail, String assignedCashierDisplayName,
            RegisterSessionStatus status, BigDecimal openingCash, BigDecimal expectedCash,
            BigDecimal countedCash, BigDecimal expectedCashAtClose, BigDecimal differenceCash,
            UUID closedByUserId, String closedByEmail, String closedByDisplayName, Instant closedAt,
            String forceCloseReason, CashLedgerBreakdownResponse reconciliation, Instant openedAt,
            Instant createdAt, Instant updatedAt, long version) {
        this(id, storeId, registerId, RegisterType.RETAIL, deviceId, null, assignedCashierId, assignedCashierEmail,
                assignedCashierDisplayName, assignedCashierId, assignedCashierDisplayName, status, openingCash, expectedCash, countedCash,
                expectedCashAtClose, differenceCash, closedByUserId, closedByEmail, closedByDisplayName,
                closedAt, forceCloseReason, null, reconciliation, openedAt, createdAt, updatedAt, version, false, null, null, false,
                SalesClassification.zero(), null, null, null, null, null, null, "USD", null);
    }

    static RegisterSessionResponse from(RegisterSession session, BigDecimal expectedCash) {
        return new RegisterSessionResponse(
                session.getId(),
                session.getStore().getId(),
                session.getRegister().getId(),
                session.getRegister().getType(),
                session.getDevice() == null ? null : session.getDevice().getId(),
                session.getDevice() == null ? null : session.getDevice().getDisplayName(),
                session.getAssignedCashier().getId(),
                session.getAssignedCashier().getEmail(),
                session.getAssignedCashier().getDisplayName(),
                session.getOpenedBy() == null ? null : session.getOpenedBy().getId(),
                session.getOpenedBy() == null ? null : session.getOpenedBy().getDisplayName(),
                session.getStatus(),
                session.getOpeningCash(),
                expectedCash,
                session.getCountedCash(),
                session.getExpectedCashAtClose(),
                session.getDifferenceCash(),
                session.getClosedBy() == null ? null : session.getClosedBy().getId(),
                session.getClosedBy() == null ? null : session.getClosedBy().getEmail(),
                session.getClosedBy() == null ? null : session.getClosedBy().getDisplayName(),
                session.getClosedAt(),
                session.getForceCloseReason(),
                session.getVarianceExplanation(),
                null,
                session.getOpenedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getVersion(), session.isTillSecured(), session.getTillSecuredAt(),
                session.getTillPinLockedUntil(), session.getAssignedCashier().hasPosPin(), SalesClassification.zero(),
                session.getTargetFloatAtClose(), session.getCashRetained(), session.getCashRemoved(),
                session.getRetentionOverrideBy() == null ? null : session.getRetentionOverrideBy().getId(),
                session.getRetentionOverrideBy() == null ? null : session.getRetentionOverrideBy().getDisplayName(),
                session.getRetentionOverrideReason(), session.getStore().getCurrencyCode(), session.getOpeningSource());
    }

    static RegisterSessionResponse from(RegisterSession session, CashLedgerBreakdownResponse reconciliation) {
        return from(session, reconciliation, SalesClassification.zero());
    }

    static RegisterSessionResponse from(RegisterSession session, CashLedgerBreakdownResponse reconciliation,
                                        SalesClassification salesClassification) {
        return new RegisterSessionResponse(
                session.getId(),
                session.getStore().getId(),
                session.getRegister().getId(),
                session.getRegister().getType(),
                session.getDevice() == null ? null : session.getDevice().getId(),
                session.getDevice() == null ? null : session.getDevice().getDisplayName(),
                session.getAssignedCashier().getId(),
                session.getAssignedCashier().getEmail(),
                session.getAssignedCashier().getDisplayName(),
                session.getOpenedBy() == null ? null : session.getOpenedBy().getId(),
                session.getOpenedBy() == null ? null : session.getOpenedBy().getDisplayName(),
                session.getStatus(),
                session.getOpeningCash(),
                session.getExpectedCashAtClose() == null ? reconciliation.expectedCash() : session.getExpectedCashAtClose(),
                session.getCountedCash(),
                session.getExpectedCashAtClose(),
                session.getDifferenceCash(),
                session.getClosedBy() == null ? null : session.getClosedBy().getId(),
                session.getClosedBy() == null ? null : session.getClosedBy().getEmail(),
                session.getClosedBy() == null ? null : session.getClosedBy().getDisplayName(),
                session.getClosedAt(),
                session.getForceCloseReason(),
                session.getVarianceExplanation(),
                reconciliation,
                session.getOpenedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getVersion(), session.isTillSecured(), session.getTillSecuredAt(),
                session.getTillPinLockedUntil(), session.getAssignedCashier().hasPosPin(), salesClassification,
                session.getTargetFloatAtClose(), session.getCashRetained(), session.getCashRemoved(),
                session.getRetentionOverrideBy() == null ? null : session.getRetentionOverrideBy().getId(),
                session.getRetentionOverrideBy() == null ? null : session.getRetentionOverrideBy().getDisplayName(),
                session.getRetentionOverrideReason(), session.getStore().getCurrencyCode(), session.getOpeningSource());
    }
}
