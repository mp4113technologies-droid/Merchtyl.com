package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Override
    @EntityGraph(attributePaths = {"unitOfMeasure", "category", "brand"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"unitOfMeasure", "category", "brand"})
    Optional<Product> findById(UUID id);

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
    @EntityGraph(attributePaths = {"variants", "barcodes", "category", "brand", "unitOfMeasure"})
    java.util.List<Product> findAllByTenantId(UUID tenantId);
    Optional<Product> findByTenantIdAndProductReferenceIgnoreCase(UUID tenantId, String productReference);

    boolean existsBySkuIgnoreCase(String sku);
    boolean existsByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
    boolean existsByTenantIdAndSkuIgnoreCaseAndIdNot(UUID tenantId, String sku, UUID id);
    boolean existsByTenantIdAndProductReferenceIgnoreCase(UUID tenantId, String productReference);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    @EntityGraph(attributePaths = {"unitOfMeasure", "category", "brand"})
    Optional<Product> findBySkuIgnoreCase(String sku);
}
