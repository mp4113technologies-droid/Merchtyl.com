package com.merchtyl.returns;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
import com.merchtyl.sales.SaleItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "return_items")
public class ReturnItem extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false, foreignKey = @ForeignKey(name = "fk_return_items_return"))
    private Return returnRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_sale_item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_return_items_sale_item"))
    private SaleItem originalSaleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_return_items_product"))
    private Product product;

    @Column(nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 64)
    private String productSku;

    @Column(nullable = false, length = 180)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal originalQuantity;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal originalUnitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalDiscountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalLineSubtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalTaxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalLineTotal;

    @Column(precision = 19, scale = 4)
    private BigDecimal originalProductCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal originalProductPrice;

    @Column(length = 1000)
    private String originalProductCapabilities;

    private UUID originalProductTaxCategoryId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal returnSubtotalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal returnTaxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal returnTotalAmount;

    protected ReturnItem() {
    }

    ReturnItem(Return returnRecord, SaleItem originalSaleItem, BigDecimal quantity, String reason) {
        this(returnRecord,
                originalSaleItem,
                quantity,
                reason,
                prorate(originalSaleItem.getLineSubtotal(), originalSaleItem.getQuantity(), quantity),
                prorate(originalSaleItem.getEstimatedTaxAmount(), originalSaleItem.getQuantity(), quantity),
                prorate(originalSaleItem.getLineTotal(), originalSaleItem.getQuantity(), quantity));
    }

    ReturnItem(
            Return returnRecord,
            SaleItem originalSaleItem,
            BigDecimal quantity,
            String reason,
            BigDecimal returnSubtotalAmount,
            BigDecimal returnTaxAmount,
            BigDecimal returnTotalAmount) {
        this.returnRecord = returnRecord;
        this.originalSaleItem = originalSaleItem;
        this.product = originalSaleItem.getProduct();
        this.productSku = originalSaleItem.getProductSku();
        this.productName = originalSaleItem.getProductName();
        this.quantity = quantity;
        this.reason = reason;
        this.originalQuantity = originalSaleItem.getQuantity();
        this.originalUnitPrice = originalSaleItem.getUnitPrice();
        this.originalDiscountAmount = originalSaleItem.getDiscountAmount();
        this.originalLineSubtotal = originalSaleItem.getLineSubtotal();
        this.originalTaxAmount = originalSaleItem.getEstimatedTaxAmount();
        this.originalLineTotal = originalSaleItem.getLineTotal();
        this.originalProductCost = originalSaleItem.getCompletedProductCost();
        this.originalProductPrice = originalSaleItem.getCompletedProductPrice();
        this.originalProductCapabilities = originalSaleItem.getCompletedProductCapabilities();
        this.originalProductTaxCategoryId = originalSaleItem.getProduct().getTaxCategoryId();
        this.returnSubtotalAmount = returnSubtotalAmount;
        this.returnTaxAmount = returnTaxAmount;
        this.returnTotalAmount = returnTotalAmount;
        initializeIdAndTimestamps();
    }

    void assignLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Return getReturnRecord() {
        return returnRecord;
    }

    public SaleItem getOriginalSaleItem() {
        return originalSaleItem;
    }

    public Product getProduct() {
        return product;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getOriginalQuantity() {
        return originalQuantity;
    }

    public BigDecimal getOriginalUnitPrice() {
        return originalUnitPrice;
    }

    public BigDecimal getOriginalDiscountAmount() {
        return originalDiscountAmount;
    }

    public BigDecimal getOriginalLineSubtotal() {
        return originalLineSubtotal;
    }

    public BigDecimal getOriginalTaxAmount() {
        return originalTaxAmount;
    }

    public BigDecimal getOriginalLineTotal() {
        return originalLineTotal;
    }

    public BigDecimal getOriginalProductCost() {
        return originalProductCost;
    }

    public BigDecimal getOriginalProductPrice() {
        return originalProductPrice;
    }

    public String getOriginalProductCapabilities() {
        return originalProductCapabilities;
    }

    public UUID getOriginalProductTaxCategoryId() {
        return originalProductTaxCategoryId;
    }

    public BigDecimal getReturnSubtotalAmount() {
        return returnSubtotalAmount;
    }

    public BigDecimal getReturnTaxAmount() {
        return returnTaxAmount;
    }

    public BigDecimal getReturnTotalAmount() {
        return returnTotalAmount;
    }

    private static BigDecimal prorate(BigDecimal originalAmount, BigDecimal originalQuantity, BigDecimal returnQuantity) {
        return originalAmount
                .multiply(returnQuantity)
                .divide(originalQuantity, 2, RoundingMode.HALF_UP);
    }
}
