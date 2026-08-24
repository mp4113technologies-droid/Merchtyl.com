package com.merchtyl.product;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "product_barcodes")
public class ProductBarcode extends BaseUuidEntity {
    @Column(name = "tenant_id")
    private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_barcodes_product"))
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", foreignKey = @ForeignKey(name = "fk_product_barcodes_variant"))
    private ProductVariant variant;

    @Column(nullable = false, length = 128)
    private String barcode;

    @Column(name = "primary_barcode", nullable = false)
    private boolean primaryBarcode;

    @Column(nullable = false)
    private boolean active;

    protected ProductBarcode() {
    }

    ProductBarcode(Product product, ProductVariant variant, ProductBarcodeValues values) {
        this.product = product;
        this.tenantId = product.getTenantId();
        this.variant = variant;
        update(values, variant);
        initializeIdAndTimestamps();
    }

    void update(ProductBarcodeValues values, ProductVariant variant) {
        this.variant = variant;
        this.barcode = values.barcode();
        this.primaryBarcode = values.primaryBarcode();
        this.active = values.active();
    }

    public Product getProduct() {
        return product;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    void assignTenant(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public String getBarcode() {
        return barcode;
    }

    public boolean isPrimaryBarcode() {
        return primaryBarcode;
    }

    public boolean isActive() {
        return active;
    }
}
