package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tax_types", uniqueConstraints = @UniqueConstraint(name = "uq_tax_types_code", columnNames = "code"))
public class TaxType extends BaseUuidEntity {
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active;

    protected TaxType() {
    }

    public TaxType(String code, String name, String description, boolean active) {
        update(code, name, description, active);
        initializeIdAndTimestamps();
    }

    public void update(String code, String name, String description, boolean active) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
