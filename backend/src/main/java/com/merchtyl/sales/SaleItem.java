package com.merchtyl.sales;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_items_product"))
    private Product product;

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

    void snapshotForCompletion() {
        this.completedProductCost = product.getCost();
        this.completedProductPrice = product.getPrice();
        this.completedProductCapabilities = product.getCapabilities().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    SaleItemRequest validationRequest() {
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
