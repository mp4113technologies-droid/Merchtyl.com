package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotteryPayoutReversalRepository extends JpaRepository<LotteryPayoutReversal, UUID>, JpaSpecificationExecutor<LotteryPayoutReversal> {
    @Override
    @EntityGraph(attributePaths = {"originalPayout", "reversedBy"})
    List<LotteryPayoutReversal> findAll(Specification<LotteryPayoutReversal> specification, Sort sort);

    boolean existsByOriginalPayout_Id(UUID originalPayoutId);

    @EntityGraph(attributePaths = {"originalPayout", "reversedBy"})
    Optional<LotteryPayoutReversal> findByOriginalPayout_Id(UUID originalPayoutId);

    @Query("""
            select coalesce(sum(reversal.amount), 0)
            from LotteryPayoutReversal reversal
            where reversal.originalPayout.operator.id = :operatorId
              and reversal.originalPayout.store.id = :storeId
              and reversal.reversedAt >= :reversedFrom
              and reversal.reversedAt < :reversedTo
            """)
    BigDecimal sumSettlementAdjustments(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("reversedFrom") Instant reversedFrom,
            @Param("reversedTo") Instant reversedTo);

    @Query("""
            select count(reversal)
            from LotteryPayoutReversal reversal
            where reversal.originalPayout.operator.id = :operatorId
              and reversal.originalPayout.store.id = :storeId
              and reversal.reversedAt >= :reversedFrom
              and reversal.reversedAt < :reversedTo
            """)
    long countSettlementAdjustments(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("reversedFrom") Instant reversedFrom,
            @Param("reversedTo") Instant reversedTo);
}
