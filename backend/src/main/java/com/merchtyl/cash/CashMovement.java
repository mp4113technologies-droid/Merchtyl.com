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
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_movements")
public class CashMovement extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cash_movements_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cash_movements_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cash_movements_register_session"))
    private RegisterSession registerSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CashMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CashLedgerDirection direction;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_cash_movements_created_by"))
    private User createdBy;

    @Column(nullable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_cash_movements_approved_by"))
    private User approvedBy;

    @Column
    private Instant approvedAt;

    @Column(length = 1000)
    private String approvalNotes;

    protected CashMovement() {
    }

    CashMovement(
            Store store,
            Register register,
            RegisterSession registerSession,
            CashMovementType type,
            CashLedgerDirection direction,
            BigDecimal amount,
            String currencyCode,
            String reason,
            String notes,
            User createdBy,
            Instant occurredAt,
            User approvedBy,
            Instant approvedAt,
            String approvalNotes) {
        this.store = store;
        this.register = register;
        this.registerSession = registerSession;
        this.type = type;
        this.direction = direction;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.reason = reason;
        this.notes = notes;
        this.createdBy = createdBy;
        this.occurredAt = occurredAt;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.approvalNotes = approvalNotes;
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

    public CashMovementType getType() {
        return type;
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

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getApprovalNotes() {
        return approvalNotes;
    }
}
