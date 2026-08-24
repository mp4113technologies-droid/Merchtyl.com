package com.merchtyl.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID>, JpaSpecificationExecutor<InventoryBalance> {
    @Override
    @EntityGraph(attributePaths = {"store", "product", "product.category"})
    List<InventoryBalance> findAll(Specification<InventoryBalance> specification, Sort sort);

    Optional<InventoryBalance> findByStoreIdAndProductId(UUID storeId, UUID productId);
}
