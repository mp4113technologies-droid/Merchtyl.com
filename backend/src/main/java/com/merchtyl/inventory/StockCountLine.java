package com.merchtyl.inventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
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
@Table(name = "stock_count_lines")
public class StockCountLine extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_count_lines_stock_count"))
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_count_lines_product"))
    private Product product;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal countedQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal varianceQuantity;

    @Column
    private Long balanceVersion;

    @Column(precision = 19, scale = 4)
    private BigDecimal resultingQuantity;

    @Column
    private UUID inventoryTransactionId;

    protected StockCountLine() {
    }

    public StockCountLine(
            StockCount stockCount,
            Product product,
            BigDecimal expectedQuantity,
            BigDecimal countedQuantity,
            Long balanceVersion) {
        this.stockCount = stockCount;
        this.product = product;
        this.expectedQuantity = expectedQuantity;
        this.balanceVersion = balanceVersion;
        initializeIdAndTimestamps();
        if (countedQuantity != null) {
            enterCountedQuantity(countedQuantity);
        }
    }

    public void enterCountedQuantity(BigDecimal countedQuantity) {
        this.countedQuantity = countedQuantity;
        this.varianceQuantity = countedQuantity.subtract(expectedQuantity).setScale(expectedQuantity.scale());
    }

    public void recount(BigDecimal currentQuantity, BigDecimal countedQuantity, Long balanceVersion) {
        this.expectedQuantity = currentQuantity;
        this.balanceVersion = balanceVersion;
        this.countedQuantity = countedQuantity;
        this.varianceQuantity = countedQuantity.subtract(currentQuantity).setScale(currentQuantity.scale());
        this.resultingQuantity = null;
        this.inventoryTransactionId = null;
    }

    public void completePost(UUID inventoryTransactionId, BigDecimal resultingQuantity) {
        this.inventoryTransactionId = inventoryTransactionId;
        this.resultingQuantity = resultingQuantity;
    }

    public StockCount getStockCount() {
        return stockCount;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getExpectedQuantity() {
        return expectedQuantity;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getVarianceQuantity() {
        return varianceQuantity;
    }

    public Long getBalanceVersion() {
        return balanceVersion;
    }

    public BigDecimal getResultingQuantity() {
        return resultingQuantity;
    }

    public UUID getInventoryTransactionId() {
        return inventoryTransactionId;
    }
}
