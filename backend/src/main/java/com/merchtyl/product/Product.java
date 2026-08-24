package com.merchtyl.product;

import com.merchtyl.catalogue.Brand;
import com.merchtyl.catalogue.Category;
import com.merchtyl.catalogue.UnitOfMeasure;
import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "products")
public class Product extends BaseUuidEntity {
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SellableType sellableType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id", foreignKey = @ForeignKey(name = "fk_products_unit"))
    private UnitOfMeasure unitOfMeasure;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cost;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_products_category"))
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", foreignKey = @ForeignKey(name = "fk_products_brand"))
    private Brand brand;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean inventoryTrackingEnabled;

    @Column(nullable = false)
    private boolean decimalQuantityAllowed;

    @Column(length = 1000)
    private String imageUrl;

    private UUID taxCategoryId;

    @Column(name = "minimum_age")
    private Integer minimumAge;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("barcode ASC")
    private List<ProductBarcode> barcodes = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("capability ASC")
    private List<ProductCapabilityAssignment> capabilityAssignments = new ArrayList<>();

    protected Product() {
    }

    public Product(ProductValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    public void update(ProductValues values) {
        this.sku = values.sku();
        this.name = values.name();
        this.description = values.description();
        this.sellableType = values.sellableType();
        this.unitOfMeasure = values.unitOfMeasure();
        this.cost = values.cost();
        this.price = values.price();
        this.category = values.category();
        this.brand = values.brand();
        this.active = values.active();
        this.inventoryTrackingEnabled = values.inventoryTrackingEnabled();
        this.decimalQuantityAllowed = values.decimalQuantityAllowed();
        this.imageUrl = values.imageUrl();
        this.taxCategoryId = values.taxCategoryId();
        replaceChildren(values.variants(), values.barcodes());
        if (tenantId != null) {
            variants.forEach(variant -> variant.assignTenant(tenantId));
            barcodes.forEach(barcode -> barcode.assignTenant(tenantId));
        }
        replaceCapabilities(values.capabilities());
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setTaxCategoryId(UUID taxCategoryId) {
        this.taxCategoryId = taxCategoryId;
    }

    public void setMinimumAge(Integer minimumAge) {
        this.minimumAge = minimumAge;
    }

    public void assignTenant(UUID tenantId) {
        if (this.tenantId != null && !this.tenantId.equals(tenantId)) {
            throw new IllegalStateException("Product tenant cannot be changed");
        }
        this.tenantId = tenantId;
        barcodes.forEach(barcode -> barcode.assignTenant(tenantId));
        variants.forEach(variant -> variant.assignTenant(tenantId));
    }

    public UUID getTenantId() {
        return tenantId;
    }

    private void replaceChildren(List<ProductVariantValues> variantValues, List<ProductBarcodeValues> barcodeValues) {
        barcodes.clear();
        replaceVariants(variantValues);
        replaceBarcodes(barcodeValues);
    }

    private void replaceVariants(List<ProductVariantValues> values) {
        variants.clear();
        values.forEach(value -> variants.add(new ProductVariant(this, value)));
    }

    private void replaceBarcodes(List<ProductBarcodeValues> values) {
        barcodes.clear();
        values.forEach(value -> barcodes.add(new ProductBarcode(this, variantBySku(value.variantSku()), value)));
    }

    private void replaceCapabilities(Set<ProductCapability> capabilities) {
        capabilityAssignments.clear();
        capabilities.forEach(capability -> capabilityAssignments.add(new ProductCapabilityAssignment(this, capability)));
    }

    private ProductVariant variantBySku(String sku) {
        if (sku == null) {
            return null;
        }
        return variants.stream()
                .filter(variant -> variant.getSku().equals(sku))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Barcode variant SKU does not belong to product"));
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SellableType getSellableType() {
        return sellableType;
    }

    public UnitOfMeasure getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Category getCategory() {
        return category;
    }

    public Brand getBrand() {
        return brand;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isInventoryTrackingEnabled() {
        return inventoryTrackingEnabled;
    }

    public boolean isDecimalQuantityAllowed() {
        return decimalQuantityAllowed;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public UUID getTaxCategoryId() {
        return taxCategoryId;
    }

    public Integer getMinimumAge() {
        return minimumAge;
    }

    public List<ProductVariant> getVariants() {
        return Collections.unmodifiableList(variants);
    }

    public List<ProductBarcode> getBarcodes() {
        return Collections.unmodifiableList(barcodes);
    }

    public Set<ProductCapability> getCapabilities() {
        EnumSet<ProductCapability> capabilities = capabilityAssignments.stream()
                .map(ProductCapabilityAssignment::getCapability)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProductCapability.class)));
        if (inventoryTrackingEnabled) {
            capabilities.add(ProductCapability.TRACK_INVENTORY);
        }
        if (decimalQuantityAllowed) {
            capabilities.add(ProductCapability.ALLOW_DECIMAL_QUANTITY);
        }
        return Collections.unmodifiableSet(capabilities);
    }

    public boolean hasCapability(ProductCapability capability) {
        return getCapabilities().contains(capability);
    }
}
