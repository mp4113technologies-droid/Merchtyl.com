package com.merchtyl.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category>, CatalogueReferenceRepository<Category>, TenantCatalogueReferenceRepository<Category> {
    java.util.Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);
    java.util.List<Category> findAllByTenantId(UUID tenantId);
}
