package com.merchtyl.supplier;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "product_suppliers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_suppliers_product_supplier",
                columnNames = {"product_id", "supplier_id"}))
public class ProductSupplier extends BaseUuidEntity {
    @Column(nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_suppliers_supplier"))
    private Supplier supplier;

    @Column(length = 128)
    private String supplierSku;

    @Column(nullable = false)
    private boolean preferred;

    @Column(nullable = false)
    private boolean active;

    protected ProductSupplier() {
    }

    public ProductSupplier(ProductSupplierValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    public void update(ProductSupplierValues values) {
        this.productId = values.product().getId();
        this.supplier = values.supplier();
        this.supplierSku = values.supplierSku();
        this.preferred = values.preferred();
        this.active = values.active();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getProductId() {
        return productId;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public boolean isPreferred() {
        return preferred;
    }

    public boolean isActive() {
        return active;
    }
}
