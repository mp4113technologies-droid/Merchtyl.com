package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "end_of_day_tax_summaries",
        uniqueConstraints = @UniqueConstraint(name = "uq_eod_tax_summaries_component", columnNames = {"report_id", "component_code"}))
public class EndOfDayTaxSummary extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_tax_summaries_report"))
    private EndOfDayReport report;

    @Column(nullable = false, updatable = false, length = 80)
    private String componentCode;

    @Column(nullable = false, updatable = false, length = 180)
    private String componentName;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxableSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal exemptSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal zeroRatedSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal outOfScopeSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxCollected;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxRefunded;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal roundingAdjustment;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal netTaxCollected;

    protected EndOfDayTaxSummary() {
    }

    public EndOfDayTaxSummary(EndOfDayReport report, String componentCode, String componentName, BigDecimal taxableSales, BigDecimal exemptSales, BigDecimal zeroRatedSales, BigDecimal outOfScopeSales, BigDecimal taxCollected, BigDecimal taxRefunded, BigDecimal roundingAdjustment) {
        this.report = report;
        this.componentCode = componentCode;
        this.componentName = componentName;
        this.taxableSales = taxableSales;
        this.exemptSales = exemptSales;
        this.zeroRatedSales = zeroRatedSales;
        this.outOfScopeSales = outOfScopeSales;
        this.taxCollected = taxCollected;
        this.taxRefunded = taxRefunded;
        this.roundingAdjustment = roundingAdjustment;
        this.netTaxCollected = taxCollected.subtract(taxRefunded).add(roundingAdjustment);
        initializeIdAndTimestamps();
    }

    public String getComponentCode() { return componentCode; }
    public String getComponentName() { return componentName; }
    public BigDecimal getTaxableSales() { return taxableSales; }
    public BigDecimal getExemptSales() { return exemptSales; }
    public BigDecimal getZeroRatedSales() { return zeroRatedSales; }
    public BigDecimal getOutOfScopeSales() { return outOfScopeSales; }
    public BigDecimal getTaxCollected() { return taxCollected; }
    public BigDecimal getTaxRefunded() { return taxRefunded; }
    public BigDecimal getRoundingAdjustment() { return roundingAdjustment; }
    public BigDecimal getNetTaxCollected() { return netTaxCollected; }
}
