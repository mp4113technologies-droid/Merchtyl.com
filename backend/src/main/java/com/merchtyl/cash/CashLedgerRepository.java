package com.merchtyl.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

public interface CashLedgerRepository extends JpaRepository<CashLedgerEntry, UUID> {
    boolean existsByOperationId(UUID operationId);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN amount ELSE -amount END), 0)
            FROM cash_ledger_entries
            WHERE register_session_id = :registerSessionId
            """, nativeQuery = true)
    BigDecimal calculateExpectedCash(@Param("registerSessionId") UUID registerSessionId);

    List<CashLedgerEntry> findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(UUID registerSessionId);

    List<CashLedgerEntry> findByRegisterSession_IdInOrderByRegisterSession_IdAscOccurredAtAscCreatedAtAsc(
            Collection<UUID> registerSessionIds);

    @Query("""
            select coalesce(sum(entry.amount), 0)
            from CashLedgerEntry entry
            where entry.sourceType = com.merchtyl.cash.CashLedgerSourceType.LOTTERY_PAYOUT_CASH
              and entry.direction = com.merchtyl.cash.CashLedgerDirection.OUT
              and entry.store.tenantId = :tenantId
              and (:storeId is null or entry.store.id = :storeId)
              and (:registerId is null or entry.register.id = :registerId)
              and (:cashierId is null or entry.createdBy.id = :cashierId)
              and (:dateFrom is null or entry.businessDate >= :dateFrom)
              and (:dateTo is null or entry.businessDate <= :dateTo)
            """)
    BigDecimal sumLotteryCashPayouts(
            @Param("tenantId") UUID tenantId,
            @Param("storeId") UUID storeId,
            @Param("registerId") UUID registerId,
            @Param("cashierId") UUID cashierId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
}
