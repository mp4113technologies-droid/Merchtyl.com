package com.merchtyl.receipts;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface KitchenPrintJobRepository extends JpaRepository<KitchenPrintJob, UUID> {
    Optional<KitchenPrintJob> findBySaleIdAndType(UUID saleId, PrintDocumentType type);
    List<KitchenPrintJob> findByStoreIdOrderByScheduledAtAsc(UUID storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<KitchenPrintJob> findFirstByStoreIdAndStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            UUID storeId, Collection<KitchenPrintJobStatus> statuses, Instant dueAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from KitchenPrintJob j where j.id=:id")
    Optional<KitchenPrintJob> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from KitchenPrintJob j where j.saleId=:saleId and j.type=:type")
    Optional<KitchenPrintJob> findBySaleIdAndTypeForUpdate(UUID saleId, PrintDocumentType type);

    List<KitchenPrintJob> findByStatusAndLastAttemptAtBefore(KitchenPrintJobStatus status, Instant before);
}
