package com.merchtyl.registersession;

import java.math.BigDecimal;
import java.time.Instant;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import java.util.UUID;
import com.merchtyl.register.RegisterType;

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
        CashLedgerBreakdownResponse reconciliation,
        Instant openedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
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
                closedAt, forceCloseReason, reconciliation, openedAt, createdAt, updatedAt, version);
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
                null,
                session.getOpenedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getVersion());
    }

    static RegisterSessionResponse from(RegisterSession session, CashLedgerBreakdownResponse reconciliation) {
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
                reconciliation.expectedCash(),
                session.getCountedCash(),
                session.getExpectedCashAtClose(),
                session.getDifferenceCash(),
                session.getClosedBy() == null ? null : session.getClosedBy().getId(),
                session.getClosedBy() == null ? null : session.getClosedBy().getEmail(),
                session.getClosedBy() == null ? null : session.getClosedBy().getDisplayName(),
                session.getClosedAt(),
                session.getForceCloseReason(),
                reconciliation,
                session.getOpenedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getVersion());
    }
}
