package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "end_of_day_category_sales_summaries")
public class EndOfDayCategorySalesSummary extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_eod_category_sales_report"))
    private EndOfDayReport report;

    @Column(updatable = false)
    private UUID categoryId;

    @Column(updatable = false, length = 64)
    private String categoryCode;

    @Column(nullable = false, updatable = false, length = 180)
    private String categoryName;

    @Column(nullable = false, updatable = false, length = 32)
    private String taxTreatment;

    @Column(nullable = false, updatable = false, length = 180)
    private String taxTreatmentLabel;

    @Column(updatable = false, length = 64)
    private String taxCategoryCode;

    @Column(updatable = false, length = 180)
    private String taxCategoryName;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal quantitySold;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal netSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal grossSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal discounts;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal refunds;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxCollected;

    @Column(nullable = false, updatable = false, precision = 7, scale = 4)
    private BigDecimal percentage;

    protected EndOfDayCategorySalesSummary() {
    }

    EndOfDayCategorySalesSummary(EndOfDayReport report, UUID categoryId, String categoryCode, String categoryName,
                                 String taxTreatment, String taxTreatmentLabel, String taxCategoryCode,
                                 String taxCategoryName, BigDecimal quantitySold, BigDecimal grossSales,
                                 BigDecimal discounts, BigDecimal refunds, BigDecimal netSales,
                                 BigDecimal taxCollected, BigDecimal percentage) {
        this.report = report;
        this.categoryId = categoryId;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.taxTreatment = taxTreatment;
        this.taxTreatmentLabel = taxTreatmentLabel;
        this.taxCategoryCode = taxCategoryCode;
        this.taxCategoryName = taxCategoryName;
        this.quantitySold = quantitySold;
        this.grossSales = grossSales;
        this.discounts = discounts;
        this.refunds = refunds;
        this.netSales = netSales;
        this.taxCollected = taxCollected;
        this.percentage = percentage;
        initializeIdAndTimestamps();
    }

    public UUID getCategoryId() { return categoryId; }
    public String getCategoryCode() { return categoryCode; }
    public String getCategoryName() { return categoryName; }
    public String getTaxTreatment() { return taxTreatment; }
    public String getTaxTreatmentLabel() { return taxTreatmentLabel; }
    public String getTaxCategoryCode() { return taxCategoryCode; }
    public String getTaxCategoryName() { return taxCategoryName; }
    public BigDecimal getQuantitySold() { return quantitySold; }
    public BigDecimal getGrossSales() { return grossSales; }
    public BigDecimal getDiscounts() { return discounts; }
    public BigDecimal getRefunds() { return refunds; }
    public BigDecimal getNetSales() { return netSales; }
    public BigDecimal getTaxCollected() { return taxCollected; }
    public BigDecimal getPercentage() { return percentage; }
}
