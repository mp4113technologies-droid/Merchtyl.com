package com.merchtyl.product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface StoreProductRepository extends JpaRepository<StoreProduct, UUID> {
 Optional<StoreProduct> findByTenantIdAndStore_IdAndProduct_Id(UUID tenantId, UUID storeId, UUID productId);
 Optional<StoreProduct> findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(UUID tenantId, UUID storeId, UUID productId);
 List<StoreProduct> findByTenantIdAndProduct_IdOrderByStore_NameAsc(UUID tenantId, UUID productId);
 List<StoreProduct> findByTenantIdAndProduct_Id(UUID tenantId, UUID productId);
 @EntityGraph(attributePaths="product") List<StoreProduct> findByTenantIdAndStore_IdAndActiveTrueAndSellableTrue(UUID tenantId, UUID storeId);
 @EntityGraph(attributePaths="product") List<StoreProduct> findByTenantIdAndStore_IdAndProduct_IdInAndActiveTrueAndSellableTrue(UUID tenantId, UUID storeId, Collection<UUID> productIds);
 void deleteByTenantIdAndProduct_Id(UUID tenantId, UUID productId);
}
