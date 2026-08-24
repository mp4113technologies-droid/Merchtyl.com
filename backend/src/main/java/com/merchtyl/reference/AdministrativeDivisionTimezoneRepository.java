package com.merchtyl.reference;

import com.merchtyl.tax.AdministrativeArea;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministrativeDivisionTimezoneRepository extends JpaRepository<AdministrativeDivisionTimezone, UUID> {
    @EntityGraph(attributePaths = {"timezone", "timezone.country"})
    List<AdministrativeDivisionTimezone> findByAdministrativeDivisionOrderByDefaultTimezoneDescTimezoneDisplayOrderAscTimezoneIanaNameAsc(AdministrativeArea administrativeDivision);

    @EntityGraph(attributePaths = {"timezone", "timezone.country"})
    Optional<AdministrativeDivisionTimezone> findByAdministrativeDivisionAndTimezone(AdministrativeArea administrativeDivision, TimezoneReference timezone);
}
