package com.merchtyl.reference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TimezoneReferenceRepository extends JpaRepository<TimezoneReference, UUID>, JpaSpecificationExecutor<TimezoneReference> {
    Optional<TimezoneReference> findByIanaNameIgnoreCase(String ianaName);
}
