package com.merchtyl.cash;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "cash_ledger_entries",
        uniqueConstraints = @UniqueConstraint(name = "uq_cash_ledger_entries_operation", columnNames = "operation_id"))
public class CashLedgerEntry extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, updatable = false)
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false, updatable = false)
    private RegisterSession registerSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private CashLedgerSourceType sourceType;

    @Column(nullable = false, updatable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private CashLedgerDirection direction;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private UUID operationId;

    @Column(length = 1000, updatable = false)
    private String notes;

    protected CashLedgerEntry() {
    }

    CashLedgerEntry(CashLedgerEntryCommand command) {
        this.store = command.store();
        this.register = command.register();
        this.registerSession = command.registerSession();
        this.sourceType = command.sourceType();
        this.sourceId = command.sourceId();
        this.direction = command.direction();
        this.amount = command.amount();
        this.currencyCode = command.currencyCode();
        this.businessDate = command.businessDate();
        this.occurredAt = command.occurredAt();
        this.createdBy = command.createdBy();
        this.operationId = command.operationId();
        this.notes = command.notes();
        initializeIdAndTimestamps();
    }

    public Store getStore() {
        return store;
    }

    public Register getRegister() {
        return register;
    }

    public RegisterSession getRegisterSession() {
        return registerSession;
    }

    public CashLedgerSourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public CashLedgerDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public String getNotes() {
        return notes;
    }
}
