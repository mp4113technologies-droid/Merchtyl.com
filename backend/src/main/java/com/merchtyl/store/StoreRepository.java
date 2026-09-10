package com.merchtyl.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface StoreRepository extends JpaRepository<Store, UUID>, JpaSpecificationExecutor<Store> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    Optional<Store> findByCodeIgnoreCase(String code);

    Optional<Store> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Store s where s.id = :id and s.tenantId = :tenantId")
    Optional<Store> findByIdAndTenantIdForUpdate(UUID id, UUID tenantId);

    List<Store> findByTenantIdAndActiveTrueOrderByNameAscIdAsc(UUID tenantId);
}
