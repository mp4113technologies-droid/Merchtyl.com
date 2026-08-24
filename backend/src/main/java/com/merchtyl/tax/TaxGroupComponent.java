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
@Table(
        name = "tax_group_components",
        uniqueConstraints = @UniqueConstraint(name = "uq_tax_group_components_group_component", columnNames = {"tax_group_id", "tax_component_id"}))
public class TaxGroupComponent extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_group_components_group"))
    private TaxGroup taxGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_component_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_group_components_component"))
    private TaxComponent taxComponent;

    @Column(nullable = false)
    private int calculationOrder;

    @Column(nullable = false)
    private boolean active;

    protected TaxGroupComponent() {
    }

    public TaxGroupComponent(TaxGroup taxGroup, TaxComponent taxComponent, int calculationOrder, boolean active) {
        update(taxGroup, taxComponent, calculationOrder, active);
        initializeIdAndTimestamps();
    }

    public void update(TaxGroup taxGroup, TaxComponent taxComponent, int calculationOrder, boolean active) {
        this.taxGroup = taxGroup;
        this.taxComponent = taxComponent;
        this.calculationOrder = calculationOrder;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public TaxGroup getTaxGroup() {
        return taxGroup;
    }

    public TaxComponent getTaxComponent() {
        return taxComponent;
    }

    public int getCalculationOrder() {
        return calculationOrder;
    }

    public boolean isActive() {
        return active;
    }
}
