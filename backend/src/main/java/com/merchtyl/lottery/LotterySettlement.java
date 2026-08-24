package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "lottery_settlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lottery_settlements_operator_store_period",
                columnNames = {"operator_id", "store_id", "period_start", "period_end"}))
public class LotterySettlement extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_settlements_operator"))
    private LotteryOperator operator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jurisdiction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_settlements_jurisdiction"))
    private TaxJurisdiction jurisdiction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_settlements_store"))
    private Store store;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grossSales;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPayouts;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cancellations;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal adjustments;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal commission;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedSettlement;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private Instant calculatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotterySettlementStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_lottery_settlements_approved_by"))
    private User approvedBy;

    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by", foreignKey = @ForeignKey(name = "fk_lottery_settlements_posted_by"))
    private User postedBy;

    private Instant postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by", foreignKey = @ForeignKey(name = "fk_lottery_settlements_reopened_by"))
    private User reopenedBy;

    private Instant reopenedAt;

    @Column(length = 1000)
    private String reopenReason;

    @Column(length = 1000)
    private String lifecycleNotes;

    protected LotterySettlement() {
    }

    LotterySettlement(LotterySettlementValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    void update(LotterySettlementValues values) {
        this.operator = values.operator();
        this.jurisdiction = values.jurisdiction();
        this.store = values.store();
        this.periodStart = values.periodStart();
        this.periodEnd = values.periodEnd();
        this.grossSales = values.grossSales();
        this.totalPayouts = values.totalPayouts();
        this.cancellations = values.cancellations();
        this.adjustments = values.adjustments();
        this.commission = values.commission();
        this.expectedSettlement = values.expectedSettlement();
        this.currencyCode = values.currencyCode();
        this.calculatedAt = values.calculatedAt();
        this.status = LotterySettlementStatus.CALCULATED;
        this.approvedBy = null;
        this.approvedAt = null;
        this.postedBy = null;
        this.postedAt = null;
        this.reopenedBy = null;
        this.reopenedAt = null;
        this.reopenReason = null;
        this.lifecycleNotes = null;
    }

    void approve(User approvedBy, Instant approvedAt, String notes) {
        this.status = LotterySettlementStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.lifecycleNotes = notes;
    }

    void post(User postedBy, Instant postedAt, String notes) {
        this.status = LotterySettlementStatus.POSTED;
        this.postedBy = postedBy;
        this.postedAt = postedAt;
        this.lifecycleNotes = notes;
    }

    void reopen(User reopenedBy, Instant reopenedAt, String reason) {
        this.status = LotterySettlementStatus.REOPENED;
        this.reopenedBy = reopenedBy;
        this.reopenedAt = reopenedAt;
        this.reopenReason = reason;
        this.lifecycleNotes = null;
    }

    public LotteryOperator getOperator() {
        return operator;
    }

    public TaxJurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public Store getStore() {
        return store;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getGrossSales() {
        return grossSales;
    }

    public BigDecimal getTotalPayouts() {
        return totalPayouts;
    }

    public BigDecimal getCancellations() {
        return cancellations;
    }

    public BigDecimal getAdjustments() {
        return adjustments;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getExpectedSettlement() {
        return expectedSettlement;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public LotterySettlementStatus getStatus() {
        return status;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public User getPostedBy() {
        return postedBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public User getReopenedBy() {
        return reopenedBy;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public String getReopenReason() {
        return reopenReason;
    }

    public String getLifecycleNotes() {
        return lifecycleNotes;
    }
}
