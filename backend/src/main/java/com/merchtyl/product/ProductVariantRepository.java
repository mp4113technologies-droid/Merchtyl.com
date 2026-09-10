package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    java.util.List<ProductVariant> findAllByTenantId(UUID tenantId);
    java.util.Optional<ProductVariant> findByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
    boolean existsByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
    boolean existsByTenantIdAndSkuIgnoreCaseAndProductIdNot(UUID tenantId, String sku, UUID productId);
    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndProductIdNot(String sku, UUID productId);
}
