package com.merchtyl.inventoryimport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
public interface InventoryImportBatchRepository extends JpaRepository<InventoryImportBatch,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select batch from InventoryImportBatch batch where batch.id=:id")
 Optional<InventoryImportBatch> findByIdForUpdate(@Param("id") UUID id);
}
