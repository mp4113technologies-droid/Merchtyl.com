package com.merchtyl.receipts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {
    Optional<Receipt> findBySale_Id(UUID saleId);

    List<Receipt> findBySale_IdIn(Collection<UUID> saleIds);

    Optional<Receipt> findByReceiptNumberIgnoreCase(String receiptNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Receipt> findForUpdateBySale_Id(UUID saleId);
}
