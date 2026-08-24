package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "lottery_sale_cancellations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lottery_sale_cancellations_original_sale", columnNames = "original_sale_id"),
                @UniqueConstraint(name = "uq_lottery_sale_cancellations_operation_id", columnNames = "operation_id")
        })
public class LotterySaleCancellation extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sale_cancellations_original_sale"))
    private LotterySale originalSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancelled_by", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sale_cancellations_cancelled_by"))
    private User cancelledBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private boolean cashReturned;

    @Column(nullable = false)
    private UUID operationId;

    @Column(nullable = false)
    private Instant cancelledAt;

    @Column(nullable = false, length = 1000)
    private String reason;

    protected LotterySaleCancellation() {
    }

    LotterySaleCancellation(LotterySale originalSale, User cancelledBy, String reason, boolean cashReturned, UUID operationId, Instant cancelledAt) {
        this.originalSale = originalSale;
        this.cancelledBy = cancelledBy;
        this.amount = originalSale.getAmount();
        this.currencyCode = originalSale.getCurrencyCode();
        this.cashReturned = cashReturned;
        this.operationId = operationId;
        this.cancelledAt = cancelledAt;
        this.reason = reason;
        initializeIdAndTimestamps();
    }

    public LotterySale getOriginalSale() {
        return originalSale;
    }

    public User getCancelledBy() {
        return cancelledBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isCashReturned() {
        return cashReturned;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getReason() {
        return reason;
    }
}
