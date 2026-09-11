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
@Table(name = "tax_categories")
public class TaxCategory extends BaseUuidEntity {
    @Column(name = "tenant_id")
    private java.util.UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 32)
    private TaxCategoryType categoryType = TaxCategoryType.STANDARD;

    @Column(name = "percentage_rate", precision = 9, scale = 4)
    private java.math.BigDecimal percentageRate;

    @Column(name = "system_managed", nullable = false)
    private boolean systemManaged = true;
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

    protected TaxCategory() {
    }

    public TaxCategory(TaxGroup taxGroup, String code, String name, TaxTreatment treatment, String description, boolean active) {
        update(taxGroup, code, name, treatment, description, active);
        initializeIdAndTimestamps();
    }

    public static TaxCategory customPercentage(java.util.UUID tenantId, String code, String name,
                                                java.math.BigDecimal percentageRate, String description, boolean active) {
        TaxCategory category = new TaxCategory(null, code, name, TaxTreatment.STANDARD, description, active);
        category.tenantId = tenantId;
        category.categoryType = TaxCategoryType.CUSTOM_PERCENTAGE;
        category.percentageRate = percentageRate;
        category.systemManaged = false;
        return category;
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

    public java.util.UUID getTenantId() { return tenantId; }
    public TaxCategoryType getCategoryType() { return categoryType; }
    public java.math.BigDecimal getPercentageRate() { return percentageRate; }
    public boolean isSystemManaged() { return systemManaged; }

    public void updateCustom(String name, java.math.BigDecimal rate, String description, boolean active) {
        this.name = name;
        this.percentageRate = rate;
        this.description = description;
        this.active = active;
    }
}
