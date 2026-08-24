package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tax_components", uniqueConstraints = @UniqueConstraint(name = "uq_tax_components_code", columnNames = "code"))
public class TaxComponent extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_components_tax_type"))
    private TaxType taxType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_jurisdiction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_components_jurisdiction"))
    private TaxJurisdiction taxJurisdiction;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active;

    protected TaxComponent() {
    }

    public TaxComponent(TaxType taxType, TaxJurisdiction taxJurisdiction, String code, String name, String description, boolean active) {
        update(taxType, taxJurisdiction, code, name, description, active);
        initializeIdAndTimestamps();
    }

    public void update(TaxType taxType, TaxJurisdiction taxJurisdiction, String code, String name, String description, boolean active) {
        this.taxType = taxType;
        this.taxJurisdiction = taxJurisdiction;
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    public TaxJurisdiction getTaxJurisdiction() {
        return taxJurisdiction;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
