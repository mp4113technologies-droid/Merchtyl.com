package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID>, JpaSpecificationExecutor<TaxRate> {
    @Override
    @EntityGraph(attributePaths = {"taxComponent"})
    Page<TaxRate> findAll(Specification<TaxRate> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"taxComponent"})
    java.util.Optional<TaxRate> findById(UUID id);

    @Query("""
            select count(rate) > 0
            from TaxRate rate
            where rate.taxComponent.id = :componentId
              and rate.status in :blockingStatuses
              and (:excludeId is null or rate.id <> :excludeId)
              and rate.effectiveFrom <= :effectiveTo
              and (rate.effectiveTo is null or rate.effectiveTo >= :effectiveFrom)
            """)
    boolean existsOverlappingActivePeriod(
            @Param("componentId") UUID componentId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("blockingStatuses") java.util.Collection<TaxRateStatus> blockingStatuses,
            @Param("excludeId") UUID excludeId);

    @Query("""
            select rate
            from TaxRate rate
            join fetch rate.taxComponent component
            where component.id in :componentIds
              and component.active = true
              and rate.status = com.merchtyl.tax.TaxRateStatus.ACTIVE
              and rate.effectiveFrom <= :effectiveOn
              and (rate.effectiveTo is null or rate.effectiveTo >= :effectiveOn)
            order by rate.calculationOrder asc, component.code asc, rate.id asc
            """)
    List<TaxRate> findActiveRatesForComponents(
            @Param("componentIds") Collection<UUID> componentIds,
            @Param("effectiveOn") LocalDate effectiveOn);
}
