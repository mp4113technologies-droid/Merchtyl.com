package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.sales.PaymentMethod;
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

@Entity
@Table(
        name = "end_of_day_payment_summaries",
        uniqueConstraints = @UniqueConstraint(name = "uq_eod_payment_summaries_method", columnNames = {"report_id", "payment_method"}))
public class EndOfDayPaymentSummary extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_payment_summaries_report"))
    private EndOfDayReport report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal collected;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal refunded;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal net;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashTendered;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal changeGiven;

    @Column(nullable = false, updatable = false)
    private long transactionCount;

    @Column(nullable = false, updatable = false)
    private long splitPaymentCount;

    protected EndOfDayPaymentSummary() {
    }

    public EndOfDayPaymentSummary(EndOfDayReport report, PaymentMethod paymentMethod, BigDecimal collected, BigDecimal refunded, BigDecimal net, BigDecimal cashTendered, BigDecimal changeGiven, long transactionCount, long splitPaymentCount) {
        this.report = report;
        this.paymentMethod = paymentMethod;
        this.collected = collected;
        this.refunded = refunded;
        this.net = net;
        this.cashTendered = cashTendered;
        this.changeGiven = changeGiven;
        this.transactionCount = transactionCount;
        this.splitPaymentCount = splitPaymentCount;
        initializeIdAndTimestamps();
    }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getCollected() { return collected; }
    public BigDecimal getRefunded() { return refunded; }
    public BigDecimal getNet() { return net; }
    public BigDecimal getCashTendered() { return cashTendered; }
    public BigDecimal getChangeGiven() { return changeGiven; }
    public long getTransactionCount() { return transactionCount; }
    public long getSplitPaymentCount() { return splitPaymentCount; }
}
