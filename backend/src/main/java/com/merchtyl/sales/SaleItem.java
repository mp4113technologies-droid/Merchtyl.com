package com.merchtyl.sales;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductVariant;
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
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "sale_items")
public class SaleItem extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_items_sale"))
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_sale_items_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SaleLineType lineType = SaleLineType.CATALOG_PRODUCT;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CustomItemTaxTreatment customItemTaxTreatment;

    private UUID taxCategorySnapshotId;

    @Column(length = 64)
    private String taxCategoryCodeSnapshot;

    @Column(length = 180)
    private String taxCategoryNameSnapshot;

    @Column(length = 32)
    private String taxCategoryTypeSnapshot;

    @Column(precision = 9, scale = 4)
    private BigDecimal taxRateSnapshot;

    @Column(precision = 12, scale = 2)
    private BigDecimal taxableAmountSnapshot;

    private UUID categorySnapshotId;

    @Column(length = 180)
    private String categoryNameSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", foreignKey = @ForeignKey(name = "fk_sale_items_variant"))
    private ProductVariant variant;

    @Column(length = 64)
    private String variantSku;

    @Column(length = 180)
    private String variantName;

    @Column(nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 64)
    private String productSku;

    @Column(nullable = false, length = 180)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private boolean priceOverride;

    @Column(nullable = false)
    private boolean ageVerified;

    @Column(length = 255)
    private String serialNumber;

    @Column(length = 255)
    private String externalReference;

    private UUID customerId;

    @Column(length = 64)
    private String paymentMethodCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedTaxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(precision = 19, scale = 4)
    private BigDecimal completedProductCost;

    @Column(precision = 19, scale = 4)
    private BigDecimal completedProductPrice;

    @Column(length = 1000)
    private String completedProductCapabilities;

    protected SaleItem() {
    }

    SaleItem(
            Sale sale,
            Product product,
            ProductVariant variant,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            boolean priceOverride,
            boolean ageVerified,
            String serialNumber,
            String externalReference,
            UUID customerId,
            String paymentMethodCode) {
        this.sale = sale;
        this.product = product;
        this.taxCategorySnapshotId = product.getTaxCategoryId();
        this.variant = variant;
        this.variantSku = variant == null ? null : variant.getSku();
        this.variantName = variant == null ? null : variant.getName();
        this.productSku = variant == null ? product.getSku() : variant.getSku();
        this.productName = variant == null ? product.getName() : product.getName() + " — " + variant.getName();
        updateInputs(quantity, unitPrice, discountAmount, priceOverride, ageVerified, serialNumber, externalReference, customerId, paymentMethodCode);
        setCalculatedAmounts(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        initializeIdAndTimestamps();
    }

    SaleItem(Sale sale, Product product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountAmount,
             boolean priceOverride, boolean ageVerified, String serialNumber, String externalReference,
             UUID customerId, String paymentMethodCode) {
        this(sale, product, null, quantity, unitPrice, discountAmount, priceOverride, ageVerified, serialNumber,
                externalReference, customerId, paymentMethodCode);
    }

    static SaleItem customItem(Sale sale, String description, BigDecimal quantity, BigDecimal unitPrice,
                               CustomItemTaxTreatment taxTreatment, UUID taxCategoryId) {
        SaleItem item = new SaleItem();
        item.sale = sale;
        item.product = null;
        item.lineType = SaleLineType.CUSTOM_ITEM;
        item.customItemTaxTreatment = taxTreatment;
        item.taxCategorySnapshotId = taxCategoryId;
        item.productSku = null;
        item.productName = description;
        item.updateInputs(quantity, unitPrice, BigDecimal.ZERO.setScale(2), false, false,
                null, null, null, null);
        item.setCalculatedAmounts(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        item.initializeIdAndTimestamps();
        return item;
    }

    void assignLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    void updateQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    void overrideUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        this.priceOverride = true;
    }

    void applyDiscount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    void updateInputs(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            boolean priceOverride,
            boolean ageVerified,
            String serialNumber,
            String externalReference,
            UUID customerId,
            String paymentMethodCode) {
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount;
        this.priceOverride = priceOverride;
        this.ageVerified = ageVerified;
        this.serialNumber = serialNumber;
        this.externalReference = externalReference;
        this.customerId = customerId;
        this.paymentMethodCode = paymentMethodCode;
    }

    void setCalculatedAmounts(BigDecimal lineSubtotal, BigDecimal estimatedTaxAmount, BigDecimal lineTotal) {
        this.lineSubtotal = lineSubtotal;
        this.estimatedTaxAmount = estimatedTaxAmount;
        this.lineTotal = lineTotal;
    }

    void setTaxSnapshot(String code, String name, String type, BigDecimal rate, BigDecimal taxableAmount) {
        this.taxCategoryCodeSnapshot = code;
        this.taxCategoryNameSnapshot = name;
        this.taxCategoryTypeSnapshot = type;
        this.taxRateSnapshot = rate;
        this.taxableAmountSnapshot = taxableAmount;
    }

    void snapshotForCompletion() {
        if (isCustomItem()) {
            completedProductCost = BigDecimal.ZERO.setScale(4);
            completedProductPrice = unitPrice;
            completedProductCapabilities = null;
            return;
        }
        this.completedProductCost = product.getCost();
        this.completedProductPrice = product.getPrice();
        this.completedProductCapabilities = product.getCapabilities().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
        if (product.getCategory() != null) {
            this.categorySnapshotId = product.getCategory().getId();
            this.categoryNameSnapshot = product.getCategory().getName();
        }
    }

    SaleItemRequest validationRequest() {
        if (isCustomItem()) {
            throw new IllegalStateException("Custom items do not use catalog item handlers");
        }
        return new SaleItemRequest(
                product,
                quantity,
                unitPrice,
                discountAmount,
                priceOverride,
                ageVerified,
                serialNumber,
                externalReference,
                customerId,
                paymentMethodCode);
    }

    public Sale getSale() {
        return sale;
    }

    public Product getProduct() {
        return product;
    }

    public SaleLineType getLineType() { return lineType; }
    public boolean isCustomItem() { return lineType == SaleLineType.CUSTOM_ITEM; }
    public CustomItemTaxTreatment getCustomItemTaxTreatment() { return customItemTaxTreatment; }
    public UUID getTaxCategorySnapshotId() { return taxCategorySnapshotId; }
    public String getTaxCategoryCodeSnapshot() { return taxCategoryCodeSnapshot; }
    public String getTaxCategoryNameSnapshot() { return taxCategoryNameSnapshot; }
    public String getTaxCategoryTypeSnapshot() { return taxCategoryTypeSnapshot; }
    public BigDecimal getTaxRateSnapshot() { return taxRateSnapshot; }
    public BigDecimal getTaxableAmountSnapshot() { return taxableAmountSnapshot; }
    public UUID getCategorySnapshotId() { return categorySnapshotId; }
    public String getCategoryNameSnapshot() { return categoryNameSnapshot; }

    public ProductVariant getVariant() { return variant; }

    public String getVariantSku() { return variantSku; }

    public String getVariantName() { return variantName; }

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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public boolean isPriceOverride() {
        return priceOverride;
    }

    public boolean isAgeVerified() {
        return ageVerified;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getPaymentMethodCode() {
        return paymentMethodCode;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public BigDecimal getEstimatedTaxAmount() {
        return estimatedTaxAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getCompletedProductCost() {
        return completedProductCost;
    }

    public BigDecimal getCompletedProductPrice() {
        return completedProductPrice;
    }

    public String getCompletedProductCapabilities() {
        return completedProductCapabilities;
    }
}
