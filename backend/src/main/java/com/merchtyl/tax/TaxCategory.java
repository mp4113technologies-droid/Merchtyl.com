package com.merchtyl.tax;

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
@Table(name = "tax_categories", uniqueConstraints = @UniqueConstraint(name = "uq_tax_categories_code", columnNames = "code"))
public class TaxCategory extends BaseUuidEntity {
    @Column(name = "owner_tenant_id")
    private java.util.UUID ownerTenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_group_id", foreignKey = @ForeignKey(name = "fk_tax_categories_group"))
    private TaxGroup taxGroup;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaxTreatment treatment;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "system_managed", nullable = false)
    private boolean systemManaged;

    @Column(name = "merchant_assignable", nullable = false)
    private boolean merchantAssignable = true;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "applicable_product_tax_class", length = 32)
    private com.merchtyl.product.ProductTaxClass applicableProductTaxClass;

    protected TaxCategory() {
    }

    public TaxCategory(TaxGroup taxGroup, String code, String name, TaxTreatment treatment, String description, boolean active) {
        update(taxGroup, code, name, treatment, description, active);
        initializeIdAndTimestamps();
    }

    public void assignMerchantOwnership(java.util.UUID tenantId) {
        this.ownerTenantId = tenantId;
        this.systemManaged = false;
        this.merchantAssignable = true;
    }

    public void update(TaxGroup taxGroup, String code, String name, TaxTreatment treatment, String description, boolean active) {
        this.taxGroup = taxGroup;
        this.code = code;
        this.name = name;
        this.treatment = treatment;
        this.description = description;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public TaxGroup getTaxGroup() {
        return taxGroup;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public TaxTreatment getTreatment() {
        return treatment;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSystemManaged() { return systemManaged; }
    public boolean isMerchantAssignable() { return merchantAssignable; }
    public java.util.UUID getOwnerTenantId() { return ownerTenantId; }
    public com.merchtyl.product.ProductTaxClass getApplicableProductTaxClass() { return applicableProductTaxClass; }
}
