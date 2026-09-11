package com.merchtyl.catalogue;

import java.util.UUID;

interface TenantCatalogueReference {
    UUID getTenantId();
    void assignTenant(UUID tenantId);
}
