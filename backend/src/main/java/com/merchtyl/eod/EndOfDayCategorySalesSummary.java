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

    @Column(nullable = false, updatable = false, length = 180)
    private String categoryName;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal quantitySold;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal netSales;

    @Column(nullable = false, updatable = false, precision = 7, scale = 4)
    private BigDecimal percentage;

    protected EndOfDayCategorySalesSummary() {
    }

    EndOfDayCategorySalesSummary(EndOfDayReport report, UUID categoryId, String categoryName,
                                 BigDecimal quantitySold, BigDecimal netSales, BigDecimal percentage) {
        this.report = report;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.quantitySold = quantitySold;
        this.netSales = netSales;
        this.percentage = percentage;
        initializeIdAndTimestamps();
    }

    public UUID getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public BigDecimal getQuantitySold() { return quantitySold; }
    public BigDecimal getNetSales() { return netSales; }
    public BigDecimal getPercentage() { return percentage; }
}
