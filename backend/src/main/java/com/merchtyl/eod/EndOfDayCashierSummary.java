package com.merchtyl.eod;

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

@Entity
@Table(
        name = "end_of_day_cashier_summaries",
        uniqueConstraints = @UniqueConstraint(name = "uq_eod_cashier_summaries_cashier", columnNames = {"report_id", "cashier_id"}))
public class EndOfDayCashierSummary extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_cashier_summaries_report"))
    private EndOfDayReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_cashier_summaries_cashier"))
    private User cashier;

    @Column(nullable = false, updatable = false, length = 180)
    private String cashierName;
    @Column(nullable = false, updatable = false)
    private long transactionCount;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal grossSales;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal netSales;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal refundTotal;
    @Column(nullable = false, updatable = false)
    private long voidCount;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal;
    @Column(nullable = false, updatable = false)
    private long priceOverrideCount;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashHandled;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotterySales;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotteryPayouts;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal averageTransactionValue;
    @Column(updatable = false)
    private Instant firstActivityAt;
    @Column(updatable = false)
    private Instant lastActivityAt;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String registersUsed;

    protected EndOfDayCashierSummary() {
    }

    public EndOfDayCashierSummary(EndOfDayReport report, User cashier, String cashierName, long transactionCount, BigDecimal grossSales, BigDecimal netSales, BigDecimal refundTotal, long voidCount, BigDecimal discountTotal, long priceOverrideCount, BigDecimal cashHandled, BigDecimal lotterySales, BigDecimal lotteryPayouts, BigDecimal averageTransactionValue, Instant firstActivityAt, Instant lastActivityAt, String registersUsed) {
        this.report = report;
        this.cashier = cashier;
        this.cashierName = cashierName;
        this.transactionCount = transactionCount;
        this.grossSales = grossSales;
        this.netSales = netSales;
        this.refundTotal = refundTotal;
        this.voidCount = voidCount;
        this.discountTotal = discountTotal;
        this.priceOverrideCount = priceOverrideCount;
        this.cashHandled = cashHandled;
        this.lotterySales = lotterySales;
        this.lotteryPayouts = lotteryPayouts;
        this.averageTransactionValue = averageTransactionValue;
        this.firstActivityAt = firstActivityAt;
        this.lastActivityAt = lastActivityAt;
        this.registersUsed = registersUsed;
        initializeIdAndTimestamps();
    }

    public User getCashier() { return cashier; }
    public String getCashierName() { return cashierName; }
    public long getTransactionCount() { return transactionCount; }
    public BigDecimal getGrossSales() { return grossSales; }
    public BigDecimal getNetSales() { return netSales; }
    public BigDecimal getRefundTotal() { return refundTotal; }
    public long getVoidCount() { return voidCount; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public long getPriceOverrideCount() { return priceOverrideCount; }
    public BigDecimal getCashHandled() { return cashHandled; }
    public BigDecimal getLotterySales() { return lotterySales; }
    public BigDecimal getLotteryPayouts() { return lotteryPayouts; }
    public BigDecimal getAverageTransactionValue() { return averageTransactionValue; }
    public Instant getFirstActivityAt() { return firstActivityAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public String getRegistersUsed() { return registersUsed; }
}
