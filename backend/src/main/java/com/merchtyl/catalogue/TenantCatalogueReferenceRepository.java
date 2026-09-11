package com.merchtyl.catalogue;

import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
interface TenantCatalogueReferenceRepository<T extends CatalogueReference> {
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(UUID tenantId, String code, UUID id);
}
