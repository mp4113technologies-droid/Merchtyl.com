package com.merchtyl.inventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
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
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment_lines")
public class StockAdjustmentLine extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjustment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_adjustment_lines_adjustment"))
    private StockAdjustment adjustment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_adjustment_lines_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockAdjustmentType adjustmentType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityDelta;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal resultingQuantity;

    @Column
    private UUID inventoryTransactionId;

    protected StockAdjustmentLine() {
    }

    public StockAdjustmentLine(
            StockAdjustment adjustment,
            Product product,
            StockAdjustmentType adjustmentType,
            BigDecimal quantity,
            BigDecimal quantityDelta) {
        this.adjustment = adjustment;
        this.product = product;
        this.adjustmentType = adjustmentType;
        this.quantity = quantity;
        this.quantityDelta = quantityDelta;
        initializeIdAndTimestamps();
    }

    public void complete(UUID inventoryTransactionId, BigDecimal resultingQuantity) {
        this.inventoryTransactionId = inventoryTransactionId;
        this.resultingQuantity = resultingQuantity;
    }

    public StockAdjustment getAdjustment() {
        return adjustment;
    }

    public Product getProduct() {
        return product;
    }

    public StockAdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getResultingQuantity() {
        return resultingQuantity;
    }

    public UUID getInventoryTransactionId() {
        return inventoryTransactionId;
    }
}
