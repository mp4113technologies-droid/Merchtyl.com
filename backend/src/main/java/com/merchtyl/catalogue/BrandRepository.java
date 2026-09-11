package com.merchtyl.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID>, JpaSpecificationExecutor<Brand>, CatalogueReferenceRepository<Brand>, TenantCatalogueReferenceRepository<Brand> {
    java.util.Optional<Brand> findByIdAndTenantId(UUID id, UUID tenantId);
    java.util.List<Brand> findAllByTenantId(UUID tenantId);
}
