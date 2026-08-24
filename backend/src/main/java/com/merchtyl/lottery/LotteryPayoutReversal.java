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
        name = "lottery_payout_reversals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lottery_payout_reversals_original_payout", columnNames = "original_payout_id"),
                @UniqueConstraint(name = "uq_lottery_payout_reversals_operation_id", columnNames = "operation_id")
        })
public class LotteryPayoutReversal extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_payout_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_reversals_original_payout"))
    private LotteryPayout originalPayout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reversed_by", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_reversals_reversed_by"))
    private User reversedBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private UUID operationId;

    @Column(nullable = false)
    private Instant reversedAt;

    @Column(nullable = false, length = 1000)
    private String reason;

    protected LotteryPayoutReversal() {
    }

    LotteryPayoutReversal(LotteryPayout originalPayout, User reversedBy, String reason, UUID operationId, Instant reversedAt) {
        this.originalPayout = originalPayout;
        this.reversedBy = reversedBy;
        this.amount = originalPayout.getAmount();
        this.currencyCode = originalPayout.getCurrencyCode();
        this.operationId = operationId;
        this.reversedAt = reversedAt;
        this.reason = reason;
        initializeIdAndTimestamps();
    }

    public LotteryPayout getOriginalPayout() {
        return originalPayout;
    }

    public User getReversedBy() {
        return reversedBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReason() {
        return reason;
    }
}
