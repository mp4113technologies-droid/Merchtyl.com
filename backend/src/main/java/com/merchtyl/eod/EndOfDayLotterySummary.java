package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "end_of_day_lottery_summaries")
public class EndOfDayLotterySummary extends BaseUuidEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, unique = true, foreignKey = @ForeignKey(name = "fk_eod_lottery_summaries_report"))
    private EndOfDayReport report;

    @Column(nullable = false, updatable = false)
    private boolean enabled;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotterySales;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotteryPayouts;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal saleCancellations;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal payoutReversals;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashLotteryActivity;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal nonCashLotteryActivity;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal commissionEarned;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal settlementAmount;
    @Column(nullable = false, updatable = false)
    private long operatorReferrals;
    @Column(nullable = false, updatable = false)
    private long pendingReferrals;
    @Column(nullable = false, updatable = false)
    private long approvalCount;
    @Column(nullable = false, updatable = false)
    private long rejectedPayouts;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String operatorTotals;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String registerTotals;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String cashierTotals;

    protected EndOfDayLotterySummary() {
    }

    public EndOfDayLotterySummary(EndOfDayReport report, boolean enabled, BigDecimal lotterySales, BigDecimal lotteryPayouts, BigDecimal saleCancellations, BigDecimal payoutReversals, BigDecimal cashLotteryActivity, BigDecimal nonCashLotteryActivity, BigDecimal commissionEarned, BigDecimal settlementAmount, long operatorReferrals, long pendingReferrals, long approvalCount, long rejectedPayouts, String operatorTotals, String registerTotals, String cashierTotals) {
        this.report = report;
        this.enabled = enabled;
        this.lotterySales = lotterySales;
        this.lotteryPayouts = lotteryPayouts;
        this.saleCancellations = saleCancellations;
        this.payoutReversals = payoutReversals;
        this.cashLotteryActivity = cashLotteryActivity;
        this.nonCashLotteryActivity = nonCashLotteryActivity;
        this.commissionEarned = commissionEarned;
        this.settlementAmount = settlementAmount;
        this.operatorReferrals = operatorReferrals;
        this.pendingReferrals = pendingReferrals;
        this.approvalCount = approvalCount;
        this.rejectedPayouts = rejectedPayouts;
        this.operatorTotals = operatorTotals;
        this.registerTotals = registerTotals;
        this.cashierTotals = cashierTotals;
        initializeIdAndTimestamps();
    }

    public boolean isEnabled() { return enabled; }
    public BigDecimal getLotterySales() { return lotterySales; }
    public BigDecimal getLotteryPayouts() { return lotteryPayouts; }
    public BigDecimal getSaleCancellations() { return saleCancellations; }
    public BigDecimal getPayoutReversals() { return payoutReversals; }
    public BigDecimal getCashLotteryActivity() { return cashLotteryActivity; }
    public BigDecimal getNonCashLotteryActivity() { return nonCashLotteryActivity; }
    public BigDecimal getCommissionEarned() { return commissionEarned; }
    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public long getOperatorReferrals() { return operatorReferrals; }
    public long getPendingReferrals() { return pendingReferrals; }
    public long getApprovalCount() { return approvalCount; }
    public long getRejectedPayouts() { return rejectedPayouts; }
    public String getOperatorTotals() { return operatorTotals; }
    public String getRegisterTotals() { return registerTotals; }
    public String getCashierTotals() { return cashierTotals; }
}
