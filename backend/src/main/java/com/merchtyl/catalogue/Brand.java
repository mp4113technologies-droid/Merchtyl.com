package com.merchtyl.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "brands",
        uniqueConstraints = @UniqueConstraint(name = "uq_brands_code", columnNames = "code"))
public class Brand extends CatalogueReference {
    protected Brand() {
    }

    public Brand(String code, String name, String description, boolean active) {
        super(code, name, description, active);
    }
}
