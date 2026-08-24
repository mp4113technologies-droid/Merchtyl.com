package com.merchtyl.reference;

import com.merchtyl.tax.AdministrativeArea;
import com.merchtyl.tax.Country;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRegionRepository extends JpaRepository<TaxRegion, UUID>, JpaSpecificationExecutor<TaxRegion> {
    @EntityGraph(attributePaths = {"country", "administrativeDivision", "taxJurisdiction"})
    Optional<TaxRegion> findByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = {"country", "administrativeDivision", "taxJurisdiction"})
    List<TaxRegion> findByAdministrativeDivisionOrderByDefaultForDivisionDescCodeAsc(AdministrativeArea administrativeDivision);

    boolean existsByCountryAndCodeIgnoreCase(Country country, String code);
}
