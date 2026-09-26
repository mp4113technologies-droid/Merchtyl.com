package com.merchtyl.posquickkey;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosQuickKeyRepository extends JpaRepository<PosQuickKey, UUID> {
    @EntityGraph(attributePaths = {"productVariant", "productVariant.product"})
    List<PosQuickKey> findByTenantIdOrderByDisplayOrderAscIdAsc(UUID tenantId);
    @EntityGraph(attributePaths = {"productVariant", "productVariant.product"})
    Optional<PosQuickKey> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndProductVariant_Id(UUID tenantId, UUID variantId);
    long countByTenantId(UUID tenantId);
}
