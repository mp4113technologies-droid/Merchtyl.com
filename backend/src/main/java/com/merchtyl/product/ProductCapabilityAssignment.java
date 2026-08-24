package com.merchtyl.product;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "product_capability_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_capability_assignments_product_capability",
                columnNames = {"product_id", "capability"}))
public class ProductCapabilityAssignment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_capability_assignments_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ProductCapability capability;

    protected ProductCapabilityAssignment() {
    }

    ProductCapabilityAssignment(Product product, ProductCapability capability) {
        this.product = product;
        this.capability = capability;
        initializeIdAndTimestamps();
    }

    public Product getProduct() {
        return product;
    }

    public ProductCapability getCapability() {
        return capability;
    }
}
