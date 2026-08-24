package com.merchtyl.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID>, JpaSpecificationExecutor<Store> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    Optional<Store> findByCodeIgnoreCase(String code);

    Optional<Store> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Store> findByTenantIdAndActiveTrueOrderByNameAscIdAsc(UUID tenantId);
}
