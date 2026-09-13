package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Override
    @EntityGraph(attributePaths = {"unitOfMeasure", "category", "brand"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"unitOfMeasure", "category", "brand"})
    @Query("select p from Product p where p.id = :id and p.deletedAt is null")
    Optional<Product> findById(@Param("id") UUID id);

    @Query("select p from Product p where p.id = :id and p.tenantId = :tenantId and p.deletedAt is null")
    Optional<Product> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
    @Query("select p from Product p where p.tenantId = :tenantId and p.deletedAt is null")
    java.util.List<Product> findAllByTenantId(@Param("tenantId") UUID tenantId);
    @Query("select p from Product p where p.tenantId = :tenantId and lower(p.productReference) = lower(:productReference) and p.deletedAt is null")
    Optional<Product> findByTenantIdAndProductReferenceIgnoreCase(@Param("tenantId") UUID tenantId, @Param("productReference") String productReference);

    boolean existsBySkuIgnoreCase(String sku);
    boolean existsByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
    boolean existsByTenantIdAndSkuIgnoreCaseAndIdNot(UUID tenantId, String sku, UUID id);
    boolean existsByTenantIdAndProductReferenceIgnoreCase(UUID tenantId, String productReference);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    @EntityGraph(attributePaths = {"unitOfMeasure", "category", "brand"})
    @Query("select p from Product p where lower(p.sku) = lower(:sku) and p.deletedAt is null")
    Optional<Product> findBySkuIgnoreCase(@Param("sku") String sku);
}
