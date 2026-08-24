package com.merchtyl.eod;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EndOfDayReportRepository extends JpaRepository<EndOfDayReport, UUID>, JpaSpecificationExecutor<EndOfDayReport> {
    @Override
    @EntityGraph(attributePaths = {"businessDay", "store", "generatedBy", "signOff", "signOff.manager"})
    Optional<EndOfDayReport> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"businessDay", "store", "generatedBy", "signOff", "signOff.manager"})
    Page<EndOfDayReport> findAll(Specification<EndOfDayReport> specification, Pageable pageable);

    @Query("select coalesce(max(report.revision), 0) from EndOfDayReport report where report.businessDay.id = :businessDayId")
    int maxRevision(@Param("businessDayId") UUID businessDayId);

    Optional<EndOfDayReport> findFirstByBusinessDay_IdOrderByRevisionDesc(UUID businessDayId);

    boolean existsByBusinessDay_Id(UUID businessDayId);
}
