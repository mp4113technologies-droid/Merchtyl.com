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

public interface LotterySaleCancellationRepository extends JpaRepository<LotterySaleCancellation, UUID>, JpaSpecificationExecutor<LotterySaleCancellation> {
    @Override
    @EntityGraph(attributePaths = {"originalSale", "cancelledBy"})
    List<LotterySaleCancellation> findAll(Specification<LotterySaleCancellation> specification, Sort sort);

    boolean existsByOriginalSale_Id(UUID originalSaleId);

    @EntityGraph(attributePaths = {"originalSale", "cancelledBy"})
    Optional<LotterySaleCancellation> findByOriginalSale_Id(UUID originalSaleId);

    @Query("""
            select coalesce(sum(cancellation.amount), 0)
            from LotterySaleCancellation cancellation
            where cancellation.originalSale.operator.id = :operatorId
              and cancellation.originalSale.store.id = :storeId
              and cancellation.cancelledAt >= :cancelledFrom
              and cancellation.cancelledAt < :cancelledTo
            """)
    BigDecimal sumSettlementCancellations(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("cancelledFrom") Instant cancelledFrom,
            @Param("cancelledTo") Instant cancelledTo);

    @Query("""
            select count(cancellation)
            from LotterySaleCancellation cancellation
            where cancellation.originalSale.operator.id = :operatorId
              and cancellation.originalSale.store.id = :storeId
              and cancellation.cancelledAt >= :cancelledFrom
              and cancellation.cancelledAt < :cancelledTo
            """)
    long countSettlementCancellations(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("cancelledFrom") Instant cancelledFrom,
            @Param("cancelledTo") Instant cancelledTo);
}
