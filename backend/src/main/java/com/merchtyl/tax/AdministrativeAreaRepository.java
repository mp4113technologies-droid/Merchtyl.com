package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface AdministrativeAreaRepository extends JpaRepository<AdministrativeArea, UUID>, JpaSpecificationExecutor<AdministrativeArea> {
    @Override
    @EntityGraph(attributePaths = {"country"})
    Page<AdministrativeArea> findAll(Specification<AdministrativeArea> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"country"})
    Optional<AdministrativeArea> findById(UUID id);

    boolean existsByCountryAndCodeIgnoreCase(Country country, String code);

    boolean existsByCountryAndCodeIgnoreCaseAndIdNot(Country country, String code, UUID id);

    @EntityGraph(attributePaths = {"country", "defaultTimezone", "defaultTaxRegion"})
    Optional<AdministrativeArea> findByCountryAndCodeIgnoreCase(Country country, String code);
}
