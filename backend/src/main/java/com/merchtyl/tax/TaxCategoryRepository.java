package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface TaxCategoryRepository extends JpaRepository<TaxCategory, UUID>, JpaSpecificationExecutor<TaxCategory> {
    @Override
    @EntityGraph(attributePaths = {"taxGroup"})
    Page<TaxCategory> findAll(Specification<TaxCategory> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"taxGroup"})
    Optional<TaxCategory> findById(UUID id);

    boolean existsByCodeIgnoreCase(String code);
    Optional<TaxCategory> findByCodeIgnoreCase(String code);
    Optional<TaxCategory> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(UUID tenantId, String code, UUID id);
    boolean existsByTenantIdIsNullAndCodeIgnoreCase(String code);
}
