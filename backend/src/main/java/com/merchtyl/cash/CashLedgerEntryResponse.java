package com.merchtyl.cash;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CashLedgerEntryResponse(
        UUID id,
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        CashLedgerSourceType sourceType,
        UUID sourceId,
        CashLedgerDirection direction,
        BigDecimal amount,
        String currencyCode,
        LocalDate businessDate,
        Instant occurredAt,
        UUID createdBy,
        UUID operationId,
        String notes,
        Instant createdAt,
        long version
) {
    static CashLedgerEntryResponse from(CashLedgerEntry entry) {
        return new CashLedgerEntryResponse(
                entry.getId(),
                entry.getStore().getId(),
                entry.getRegister().getId(),
                entry.getRegisterSession().getId(),
                entry.getSourceType(),
                entry.getSourceId(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrencyCode(),
                entry.getBusinessDate(),
                entry.getOccurredAt(),
                entry.getCreatedBy().getId(),
                entry.getOperationId(),
                entry.getNotes(),
                entry.getCreatedAt(),
                entry.getVersion());
    }
}
