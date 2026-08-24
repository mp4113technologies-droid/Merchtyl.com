package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxGroupComponentRepository extends JpaRepository<TaxGroupComponent, UUID>, JpaSpecificationExecutor<TaxGroupComponent> {
    @Override
    @EntityGraph(attributePaths = {"taxGroup", "taxComponent"})
    Page<TaxGroupComponent> findAll(Specification<TaxGroupComponent> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"taxGroup", "taxComponent"})
    Optional<TaxGroupComponent> findById(UUID id);

    boolean existsByTaxGroupAndTaxComponent(TaxGroup taxGroup, TaxComponent taxComponent);

    boolean existsByTaxGroupAndTaxComponentAndIdNot(TaxGroup taxGroup, TaxComponent taxComponent, UUID id);

    @EntityGraph(attributePaths = {"taxGroup", "taxComponent"})
    List<TaxGroupComponent> findByTaxGroupIdInAndActiveTrue(Collection<UUID> taxGroupIds);
}
