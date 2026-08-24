package com.merchtyl.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "units_of_measure",
        uniqueConstraints = @UniqueConstraint(name = "uq_units_of_measure_code", columnNames = "code"))
public class UnitOfMeasure extends CatalogueReference {
    protected UnitOfMeasure() {
    }

    public UnitOfMeasure(String code, String name, String description, boolean active) {
        super(code, name, description, active);
    }
}
