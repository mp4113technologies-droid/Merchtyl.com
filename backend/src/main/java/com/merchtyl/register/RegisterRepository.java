package com.merchtyl.register;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface RegisterRepository extends JpaRepository<Register, UUID>, JpaSpecificationExecutor<Register> {
    boolean existsByStore_IdAndCodeIgnoreCase(UUID storeId, String code);

    boolean existsByStore_IdAndCodeIgnoreCaseAndIdNot(UUID storeId, String code, UUID id);

    Optional<Register> findByStore_IdAndCodeIgnoreCase(UUID storeId, String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select register from Register register where register.id = :id")
    Optional<Register> findByIdForUpdate(@Param("id") UUID id);
}
