package com.merchtyl.eod;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessDayConfigurationRepository extends JpaRepository<BusinessDayConfiguration, UUID> {
    Optional<BusinessDayConfiguration> findByStore_Id(UUID storeId);
}
