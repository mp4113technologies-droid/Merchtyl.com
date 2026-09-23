package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    @EntityGraph(attributePaths = "product")
    @Query("select v from ProductVariant v where v.tenantId = :tenantId and v.id in :ids and v.product.deletedAt is null")
    List<ProductVariant> findCheckoutVariants(@Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);
    @EntityGraph(attributePaths = {"product", "product.barcodes"})
    @Query("select distinct v from ProductVariant v where v.tenantId = :tenantId and v.product.deletedAt is null")
    java.util.List<ProductVariant> findAllByTenantId(@Param("tenantId") UUID tenantId);
    @Query("select v from ProductVariant v where v.tenantId = :tenantId and lower(v.sku) = lower(:sku) and v.product.deletedAt is null")
    java.util.Optional<ProductVariant> findByTenantIdAndSkuIgnoreCase(@Param("tenantId") UUID tenantId, @Param("sku") String sku);
    boolean existsByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
    boolean existsByTenantIdAndSkuIgnoreCaseAndProductIdNot(UUID tenantId, String sku, UUID productId);
    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndProductIdNot(String sku, UUID productId);
}
