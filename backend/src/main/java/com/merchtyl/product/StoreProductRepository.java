package com.merchtyl.product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface StoreProductRepository extends JpaRepository<StoreProduct, UUID> {
 Optional<StoreProduct> findByTenantIdAndStore_IdAndProduct_Id(UUID tenantId, UUID storeId, UUID productId);
 Optional<StoreProduct> findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(UUID tenantId, UUID storeId, UUID productId);
 List<StoreProduct> findByTenantIdAndProduct_IdOrderByStore_NameAsc(UUID tenantId, UUID productId);
}
