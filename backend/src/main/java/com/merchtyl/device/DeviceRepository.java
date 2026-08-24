package com.merchtyl.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {
    boolean existsByDeviceIdentifierIgnoreCase(String deviceIdentifier);

    boolean existsByDeviceIdentifierIgnoreCaseAndIdNot(String deviceIdentifier, UUID id);

    Optional<Device> findByDeviceIdentifierIgnoreCase(String deviceIdentifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from Device device where device.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") UUID id);
}
