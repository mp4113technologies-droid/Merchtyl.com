package com.merchtyl.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
}
