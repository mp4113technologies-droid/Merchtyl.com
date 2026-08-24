package com.merchtyl.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uq_categories_code", columnNames = "code"))
public class Category extends CatalogueReference {
    protected Category() {
    }

    public Category(String code, String name, String description, boolean active) {
        super(code, name, description, active);
    }
}
