package com.merchtyl.cash;

import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CashLedgerEntryCommand(
        Store store,
        Register register,
        RegisterSession registerSession,
        CashLedgerSourceType sourceType,
        UUID sourceId,
        CashLedgerDirection direction,
        BigDecimal amount,
        String currencyCode,
        LocalDate businessDate,
        Instant occurredAt,
        User createdBy,
        UUID operationId,
        String notes
) {
}
