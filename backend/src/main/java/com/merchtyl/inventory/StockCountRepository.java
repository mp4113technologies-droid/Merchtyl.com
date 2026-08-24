package com.merchtyl.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockCountRepository extends JpaRepository<StockCount, UUID>, JpaSpecificationExecutor<StockCount> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select count from StockCount count left join fetch count.lines where count.id = :id")
    Optional<StockCount> findByIdForUpdate(@Param("id") UUID id);
}
