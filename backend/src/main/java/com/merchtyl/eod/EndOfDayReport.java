package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "end_of_day_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_eod_reports_store_number", columnNames = {"store_id", "report_number"}),
                @UniqueConstraint(name = "uq_eod_reports_business_day_revision", columnNames = {"business_day_id", "revision"})
        })
public class EndOfDayReport extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_day_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_reports_business_day"))
    private BusinessDay businessDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_reports_store"))
    private Store store;

    @Column(nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, updatable = false, length = 80)
    private String reportNumber;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(nullable = false, updatable = false)
    private Instant generatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_reports_generated_by"))
    private User generatedBy;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal grossSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal netSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal refundTotal;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal voidTotal;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(nullable = false, updatable = false)
    private long transactionCount;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal averageTransactionValue;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal highestTransactionValue;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lowestTransactionValue;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal itemsSold;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal averageBasketSize;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashVariance;

    @Column(nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reportSnapshot;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("registerCode ASC")
    private final List<EndOfDayRegisterSummary> registerSummaries = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("paymentMethod ASC")
    private final List<EndOfDayPaymentSummary> paymentSummaries = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("componentCode ASC")
    private final List<EndOfDayTaxSummary> taxSummaries = new ArrayList<>();

    @OneToOne(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    private EndOfDayLotterySummary lotterySummary;

    @OneToOne(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    private EndOfDayInventorySummary inventorySummary;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("cashierName ASC")
    private final List<EndOfDayCashierSummary> cashierSummaries = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("exceptionType ASC")
    private final List<EndOfDayExceptionSummary> exceptionSummaries = new ArrayList<>();

    @OneToOne(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = false)
    private EndOfDaySignOff signOff;

    protected EndOfDayReport() {
    }

    public EndOfDayReport(
            BusinessDay businessDay,
            User generatedBy,
            String reportNumber,
            int revision,
            Instant generatedAt,
            EndOfDayReportTotals totals,
            String reportSnapshot) {
        this.businessDay = businessDay;
        this.store = businessDay.getStore();
        this.businessDate = businessDay.getBusinessDate();
        this.reportNumber = reportNumber;
        this.revision = revision;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.grossSales = totals.grossSales();
        this.netSales = totals.netSales();
        this.discountTotal = totals.discountTotal();
        this.refundTotal = totals.refundTotal();
        this.voidTotal = totals.voidTotal();
        this.taxTotal = totals.taxTotal();
        this.transactionCount = totals.transactionCount();
        this.averageTransactionValue = totals.averageTransactionValue();
        this.highestTransactionValue = totals.highestTransactionValue();
        this.lowestTransactionValue = totals.lowestTransactionValue();
        this.itemsSold = totals.itemsSold();
        this.averageBasketSize = totals.averageBasketSize();
        this.expectedCash = totals.expectedCash();
        this.countedCash = totals.countedCash();
        this.cashVariance = totals.cashVariance();
        this.currencyCode = totals.currencyCode();
        this.reportSnapshot = reportSnapshot;
        initializeIdAndTimestamps();
    }

    public void addRegisterSummary(EndOfDayRegisterSummary summary) {
        registerSummaries.add(summary);
    }

    public void addPaymentSummary(EndOfDayPaymentSummary summary) {
        paymentSummaries.add(summary);
    }

    public void addTaxSummary(EndOfDayTaxSummary summary) {
        taxSummaries.add(summary);
    }

    public void setLotterySummary(EndOfDayLotterySummary lotterySummary) {
        this.lotterySummary = lotterySummary;
    }

    public void setInventorySummary(EndOfDayInventorySummary inventorySummary) {
        this.inventorySummary = inventorySummary;
    }

    public void addCashierSummary(EndOfDayCashierSummary summary) {
        cashierSummaries.add(summary);
    }

    public void addExceptionSummary(EndOfDayExceptionSummary summary) {
        exceptionSummaries.add(summary);
    }

    public void setSignOff(EndOfDaySignOff signOff) {
        this.signOff = signOff;
    }

    public BusinessDay getBusinessDay() {
        return businessDay;
    }

    public Store getStore() {
        return store;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getReportNumber() {
        return reportNumber;
    }

    public int getRevision() {
        return revision;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public User getGeneratedBy() {
        return generatedBy;
    }

    public BigDecimal getGrossSales() {
        return grossSales;
    }

    public BigDecimal getNetSales() {
        return netSales;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getRefundTotal() {
        return refundTotal;
    }

    public BigDecimal getVoidTotal() {
        return voidTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public BigDecimal getAverageTransactionValue() {
        return averageTransactionValue;
    }

    public BigDecimal getHighestTransactionValue() {
        return highestTransactionValue;
    }

    public BigDecimal getLowestTransactionValue() {
        return lowestTransactionValue;
    }

    public BigDecimal getItemsSold() {
        return itemsSold;
    }

    public BigDecimal getAverageBasketSize() {
        return averageBasketSize;
    }

    public BigDecimal getExpectedCash() {
        return expectedCash;
    }

    public BigDecimal getCountedCash() {
        return countedCash;
    }

    public BigDecimal getCashVariance() {
        return cashVariance;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getReportSnapshot() {
        return reportSnapshot;
    }

    public List<EndOfDayRegisterSummary> getRegisterSummaries() {
        return Collections.unmodifiableList(registerSummaries);
    }

    public List<EndOfDayPaymentSummary> getPaymentSummaries() {
        return Collections.unmodifiableList(paymentSummaries);
    }

    public List<EndOfDayTaxSummary> getTaxSummaries() {
        return Collections.unmodifiableList(taxSummaries);
    }

    public EndOfDayLotterySummary getLotterySummary() {
        return lotterySummary;
    }

    public EndOfDayInventorySummary getInventorySummary() {
        return inventorySummary;
    }

    public List<EndOfDayCashierSummary> getCashierSummaries() {
        return Collections.unmodifiableList(cashierSummaries);
    }

    public List<EndOfDayExceptionSummary> getExceptionSummaries() {
        return Collections.unmodifiableList(exceptionSummaries);
    }

    public EndOfDaySignOff getSignOff() {
        return signOff;
    }
}
