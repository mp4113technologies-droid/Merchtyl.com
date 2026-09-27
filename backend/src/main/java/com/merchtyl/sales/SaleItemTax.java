package com.merchtyl.sales;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.ProductTaxClass;
import com.merchtyl.tax.TaxComponentCalculationResponse;
import com.merchtyl.tax.TaxReportingType;
import com.merchtyl.tax.TaxCategory;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable financial snapshot of one calculated tax component. */
@Entity
@Table(name = "sale_item_taxes")
public class SaleItemTax extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_item_taxes_item"))
    private SaleItem saleItem;
    @Column(name="tax_category_id") private UUID taxCategoryId;
    @Column(name="tax_category_code", length=64) private String taxCategoryCode;
    @Column(name="tax_group_id") private UUID taxGroupId;
    @Column(name="tax_group_code", length=64) private String taxGroupCode;
    @Enumerated(EnumType.STRING) @Column(name="product_tax_class", nullable=false, length=32) private ProductTaxClass productTaxClass;
    @Column(name="tax_component_id", nullable=false) private UUID taxComponentId;
    @Column(name="tax_component_code", nullable=false, length=64) private String taxComponentCode;
    @Column(name="tax_component_name", nullable=false, length=180) private String taxComponentName;
    @Enumerated(EnumType.STRING) @Column(name="reporting_type", nullable=false, length=32) private TaxReportingType reportingType;
    @Column(name="tax_rate_id", nullable=false) private UUID taxRateId;
    @Column(name="percentage_rate", nullable=false, precision=9, scale=6) private BigDecimal percentageRate;
    @Column(name="taxable_amount", nullable=false, precision=12, scale=2) private BigDecimal taxableAmount;
    @Column(name="tax_amount", nullable=false, precision=12, scale=2) private BigDecimal taxAmount;
    @Column(name="included_in_price", nullable=false) private boolean includedInPrice;
    @Column(name="compound_on_previous_tax", nullable=false) private boolean compoundOnPreviousTax;
    @Column(name="calculation_order", nullable=false) private int calculationOrder;
    @Column(name="effective_from", nullable=false) private LocalDate effectiveFrom;
    @Column(name="effective_to") private LocalDate effectiveTo;

    protected SaleItemTax() {}
    SaleItemTax(SaleItem item, TaxCategory category, TaxReportingType reportingType, TaxComponentCalculationResponse value) {
        this.saleItem=item; this.taxCategoryId=category==null?null:category.getId(); this.taxCategoryCode=category==null?null:category.getCode();
        this.taxGroupId=category==null||category.getTaxGroup()==null?null:category.getTaxGroup().getId();
        this.taxGroupCode=category==null||category.getTaxGroup()==null?null:category.getTaxGroup().getCode();
        this.productTaxClass=item.getProduct()==null?ProductTaxClass.STANDARD:item.getProduct().getTaxClass();
        this.taxComponentId=value.taxComponentId(); this.taxComponentCode=value.taxComponentCode(); this.taxComponentName=value.taxComponentName();
        this.reportingType=reportingType; this.taxRateId=value.taxRateId(); this.percentageRate=value.percentageRate();
        this.taxableAmount=value.taxableAmount(); this.taxAmount=value.taxAmount(); this.includedInPrice=value.includedInPrice();
        this.compoundOnPreviousTax=value.compoundOnPreviousTax(); this.calculationOrder=value.calculationOrder();
        this.effectiveFrom=value.effectiveFrom(); this.effectiveTo=value.effectiveTo(); initializeIdAndTimestamps();
    }
    public TaxReportingType getReportingType(){return reportingType;} public BigDecimal getTaxAmount(){return taxAmount;}
    public String getTaxComponentCode(){return taxComponentCode;}
    public String getTaxComponentName(){return taxComponentName;}
    public BigDecimal getTaxableAmount(){return taxableAmount;}
    public int getCalculationOrder(){return calculationOrder;}
    public UUID getTaxCategoryId(){return taxCategoryId;}
}
