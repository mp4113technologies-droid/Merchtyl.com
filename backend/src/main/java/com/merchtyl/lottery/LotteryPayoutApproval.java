package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
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
@Table(name = "lottery_payout_approvals")
public class LotteryPayoutApproval extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_approvals_payout"))
    private LotteryPayout payout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryPayoutApprovalType approvalType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_by", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_approvals_approved_by"))
    private User approvedBy;

    @Column(nullable = false)
    private Instant approvedAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal payoutAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(length = 1000)
    private String notes;

    protected LotteryPayoutApproval() {
    }

    LotteryPayoutApproval(
            LotteryPayout payout,
            LotteryPayoutApprovalType approvalType,
            User approvedBy,
            Instant approvedAt,
            BigDecimal payoutAmount,
            BigDecimal thresholdAmount,
            String notes) {
        this.payout = payout;
        this.approvalType = approvalType;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.payoutAmount = payoutAmount;
        this.thresholdAmount = thresholdAmount;
        this.notes = notes;
        initializeIdAndTimestamps();
    }

    public LotteryPayout getPayout() {
        return payout;
    }

    public LotteryPayoutApprovalType getApprovalType() {
        return approvalType;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public BigDecimal getPayoutAmount() {
        return payoutAmount;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public String getNotes() {
        return notes;
    }
}
