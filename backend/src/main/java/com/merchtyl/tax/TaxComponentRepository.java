package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface TaxComponentRepository extends JpaRepository<TaxComponent, UUID>, JpaSpecificationExecutor<TaxComponent> {
    @Override
    @EntityGraph(attributePaths = {"taxType", "taxJurisdiction"})
    Page<TaxComponent> findAll(Specification<TaxComponent> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"taxType", "taxJurisdiction"})
    Optional<TaxComponent> findById(UUID id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
