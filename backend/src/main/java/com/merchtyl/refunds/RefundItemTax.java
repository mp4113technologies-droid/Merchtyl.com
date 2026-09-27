package com.merchtyl.refunds;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.returns.ReturnItem;
import com.merchtyl.sales.SaleItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.merchtyl.sales.SaleItemTax;
import com.merchtyl.tax.TaxReportingType;
import java.math.RoundingMode;

@Entity
@Table(name = "refund_item_taxes")
public class RefundItemTax extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refund_item_taxes_refund"))
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_item_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refund_item_taxes_return_item"))
    private ReturnItem returnItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_sale_item_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refund_item_taxes_original_sale_item"))
    private SaleItem originalSaleItem;

    @Column(nullable = false, updatable = false)
    private int lineNumber;

    @Column(updatable = false)
    private UUID productTaxCategoryId;

    @Column(nullable = false, length = 40, updatable = false)
    private String taxComponentCode;

    @Column(nullable = false, length = 120, updatable = false)
    private String taxComponentName;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal taxableAmount;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal taxAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name="reporting_type", nullable=false, length=32, updatable=false)
    private TaxReportingType reportingType;

    protected RefundItemTax() {
    }

    RefundItemTax(Refund refund, ReturnItem returnItem) {
        this.refund = refund;
        this.returnItem = returnItem;
        this.originalSaleItem = returnItem.getOriginalSaleItem();
        this.productTaxCategoryId = returnItem.getOriginalProductTaxCategoryId();
        this.taxComponentCode = "TAX";
        this.taxComponentName = "Original sales tax";
        this.taxableAmount = returnItem.getReturnSubtotalAmount();
        this.taxAmount = returnItem.getReturnTaxAmount();
        this.currencyCode = refund.getCurrencyCode();
        this.reportingType = TaxReportingType.OTHER;
        initializeIdAndTimestamps();
    }

    RefundItemTax(Refund refund, ReturnItem returnItem, SaleItemTax original) {
        this.refund=refund; this.returnItem=returnItem; this.originalSaleItem=returnItem.getOriginalSaleItem();
        this.productTaxCategoryId=original.getTaxCategoryId(); this.taxComponentCode=original.getTaxComponentCode();
        this.taxComponentName=original.getTaxComponentName(); this.reportingType=original.getReportingType();
        this.taxableAmount=prorate(original.getTaxableAmount(), returnItem);
        this.taxAmount=prorate(original.getTaxAmount(), returnItem); this.currencyCode=refund.getCurrencyCode();
        initializeIdAndTimestamps();
    }

    private static BigDecimal prorate(BigDecimal amount, ReturnItem item) {
        return amount.multiply(item.getQuantity()).divide(item.getOriginalQuantity(), 2, RoundingMode.HALF_UP);
    }

    void assignLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Refund getRefund() {
        return refund;
    }

    public ReturnItem getReturnItem() {
        return returnItem;
    }

    public SaleItem getOriginalSaleItem() {
        return originalSaleItem;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public UUID getProductTaxCategoryId() {
        return productTaxCategoryId;
    }

    public String getTaxComponentCode() {
        return taxComponentCode;
    }

    public String getTaxComponentName() {
        return taxComponentName;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
    public TaxReportingType getReportingType() { return reportingType; }
}
