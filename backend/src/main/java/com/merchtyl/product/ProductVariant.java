package com.merchtyl.product;

import com.merchtyl.platform.persistence.BaseUuidEntity;
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
@Table(name = "product_variants")
public class ProductVariant extends BaseUuidEntity {
    @Column(name = "tenant_id")
    private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_variants_product"))
    private Product product;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cost;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    protected ProductVariant() {
    }

    ProductVariant(Product product, ProductVariantValues values) {
        this.product = product;
        update(values);
        initializeIdAndTimestamps();
    }

    void update(ProductVariantValues values) {
        this.sku = values.sku();
        this.name = values.name();
        this.description = values.description();
        this.cost = values.cost();
        this.price = values.price();
        this.active = values.active();
    }

    public Product getProduct() {
        return product;
    }

    void assignTenant(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTenantId() { return tenantId; }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
