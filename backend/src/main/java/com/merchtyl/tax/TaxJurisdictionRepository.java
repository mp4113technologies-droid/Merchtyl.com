package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaxJurisdictionRepository extends JpaRepository<TaxJurisdiction, UUID>, JpaSpecificationExecutor<TaxJurisdiction> {
    @Override
    @EntityGraph(attributePaths = {"country", "administrativeArea"})
    Page<TaxJurisdiction> findAll(Specification<TaxJurisdiction> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"country", "administrativeArea"})
    java.util.Optional<TaxJurisdiction> findById(UUID id);

    boolean existsByCountryAndCodeIgnoreCase(Country country, String code);

    boolean existsByCountryAndCodeIgnoreCaseAndIdNot(Country country, String code, UUID id);

    @EntityGraph(attributePaths = {"country", "administrativeArea"})
    @Query("""
            select jurisdiction
            from TaxJurisdiction jurisdiction
            left join jurisdiction.administrativeArea area
            where jurisdiction.active = true
              and upper(jurisdiction.country.code) = upper(:countryCode)
              and (
                (:administrativeAreaCode is not null and area is not null and upper(area.code) = upper(:administrativeAreaCode))
                or area is null
              )
            order by case when area is not null then 0 else 1 end, jurisdiction.code asc
            """)
    List<TaxJurisdiction> findBestForStore(
            @Param("countryCode") String countryCode,
            @Param("administrativeAreaCode") String administrativeAreaCode);
}
