package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    boolean existsByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
    boolean existsByTenantIdAndSkuIgnoreCaseAndProductIdNot(UUID tenantId, String sku, UUID productId);
    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndProductIdNot(String sku, UUID productId);
}
