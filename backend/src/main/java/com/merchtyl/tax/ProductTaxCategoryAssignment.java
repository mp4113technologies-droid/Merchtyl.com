package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "product_tax_category_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_tax_category_assignments_product", columnNames = "product_id"))
public class ProductTaxCategoryAssignment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_tax_category_assignments_product"))
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_tax_category_assignments_category"))
    private TaxCategory taxCategory;

    @Column(nullable = false)
    private boolean active;

    protected ProductTaxCategoryAssignment() {
    }

    public ProductTaxCategoryAssignment(Product product, TaxCategory taxCategory, boolean active) {
        update(product, taxCategory, active);
        initializeIdAndTimestamps();
    }

    public void update(Product product, TaxCategory taxCategory, boolean active) {
        this.product = product;
        this.taxCategory = taxCategory;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Product getProduct() {
        return product;
    }

    public TaxCategory getTaxCategory() {
        return taxCategory;
    }

    public boolean isActive() {
        return active;
    }
}
