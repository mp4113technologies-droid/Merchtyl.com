package com.merchtyl.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID>, JpaSpecificationExecutor<InventoryBalance> {
    @Override
    @EntityGraph(attributePaths = {"store", "product", "product.category"})
    List<InventoryBalance> findAll(Specification<InventoryBalance> specification, Sort sort);

    Optional<InventoryBalance> findByStoreIdAndProductId(UUID storeId, UUID productId);
    Optional<InventoryBalance> findByStoreIdAndProductIdAndVariantId(UUID storeId,UUID productId,UUID variantId);
    Optional<InventoryBalance> findByStoreIdAndProductIdAndVariantIsNull(UUID storeId,UUID productId);
    @EntityGraph(attributePaths = {"product", "variant"})
    List<InventoryBalance> findAllByStoreIdAndVariantIdIn(UUID storeId, List<UUID> variantIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.store.id=:storeId and b.variant.id=:variantId")
    Optional<InventoryBalance> findVariantForUpdate(@Param("storeId") UUID storeId,@Param("variantId") UUID variantId);
    void deleteByProductId(UUID productId);
}
