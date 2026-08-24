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
@Table(name = "end_of_day_inventory_summaries")
public class EndOfDayInventorySummary extends BaseUuidEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, unique = true, foreignKey = @ForeignKey(name = "fk_eod_inventory_summaries_report"))
    private EndOfDayReport report;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal deductedBySales;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal restoredByReturns;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal manualIncreases;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal manualDecreases;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal damagedQuantity;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal expiredQuantity;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal transferIn;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal transferOut;
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal stockCountVariances;
    @Column(nullable = false, updatable = false)
    private long lowStockProducts;
    @Column(nullable = false, updatable = false)
    private long negativeStockProducts;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal inventoryValueMovement;

    protected EndOfDayInventorySummary() {
    }

    public EndOfDayInventorySummary(EndOfDayReport report, BigDecimal deductedBySales, BigDecimal restoredByReturns, BigDecimal manualIncreases, BigDecimal manualDecreases, BigDecimal damagedQuantity, BigDecimal expiredQuantity, BigDecimal transferIn, BigDecimal transferOut, BigDecimal stockCountVariances, long lowStockProducts, long negativeStockProducts, BigDecimal inventoryValueMovement) {
        this.report = report;
        this.deductedBySales = deductedBySales;
        this.restoredByReturns = restoredByReturns;
        this.manualIncreases = manualIncreases;
        this.manualDecreases = manualDecreases;
        this.damagedQuantity = damagedQuantity;
        this.expiredQuantity = expiredQuantity;
        this.transferIn = transferIn;
        this.transferOut = transferOut;
        this.stockCountVariances = stockCountVariances;
        this.lowStockProducts = lowStockProducts;
        this.negativeStockProducts = negativeStockProducts;
        this.inventoryValueMovement = inventoryValueMovement;
        initializeIdAndTimestamps();
    }

    public BigDecimal getDeductedBySales() { return deductedBySales; }
    public BigDecimal getRestoredByReturns() { return restoredByReturns; }
    public BigDecimal getManualIncreases() { return manualIncreases; }
    public BigDecimal getManualDecreases() { return manualDecreases; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public BigDecimal getExpiredQuantity() { return expiredQuantity; }
    public BigDecimal getTransferIn() { return transferIn; }
    public BigDecimal getTransferOut() { return transferOut; }
    public BigDecimal getStockCountVariances() { return stockCountVariances; }
    public long getLowStockProducts() { return lowStockProducts; }
    public long getNegativeStockProducts() { return negativeStockProducts; }
    public BigDecimal getInventoryValueMovement() { return inventoryValueMovement; }
}
