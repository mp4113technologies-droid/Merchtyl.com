package com.merchtyl.catalogue;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "brands")
public class Brand extends CatalogueReference implements TenantCatalogueReference {
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    protected Brand() {
    }

    public Brand(String code, String name, String description, boolean active) {
        super(code, name, description, active);
    }

    public UUID getTenantId() { return tenantId; }

    public void assignTenant(UUID tenantId) {
        if (this.tenantId != null && !this.tenantId.equals(tenantId)) throw new IllegalStateException("Brand tenant is immutable");
        this.tenantId = tenantId;
    }
}
