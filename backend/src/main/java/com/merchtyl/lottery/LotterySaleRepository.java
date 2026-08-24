package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotterySaleRepository extends JpaRepository<LotterySale, UUID>, JpaSpecificationExecutor<LotterySale> {
    @Override
    @EntityGraph(attributePaths = {"operator", "store", "register", "device", "cashier", "registerSession"})
    Page<LotterySale> findAll(Specification<LotterySale> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"operator", "store", "register", "device", "cashier", "registerSession"})
    List<LotterySale> findAll(Specification<LotterySale> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"operator", "store", "register", "device", "cashier", "registerSession"})
    Optional<LotterySale> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"operator", "store", "register", "device", "cashier", "registerSession"})
    @Query("select sale from LotterySale sale where sale.id = :id")
    Optional<LotterySale> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(sale.amount), 0)
            from LotterySale sale
            where sale.operator.id = :operatorId
              and sale.store.id = :storeId
              and sale.status in :statuses
              and sale.occurredAt >= :occurredFrom
              and sale.occurredAt < :occurredTo
            """)
    BigDecimal sumSettlementSales(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("statuses") Collection<LotterySaleStatus> statuses,
            @Param("occurredFrom") Instant occurredFrom,
            @Param("occurredTo") Instant occurredTo);

    @Query("""
            select count(sale)
            from LotterySale sale
            where sale.operator.id = :operatorId
              and sale.store.id = :storeId
              and sale.status in :statuses
              and sale.occurredAt >= :occurredFrom
              and sale.occurredAt < :occurredTo
            """)
    long countSettlementSales(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("statuses") Collection<LotterySaleStatus> statuses,
            @Param("occurredFrom") Instant occurredFrom,
            @Param("occurredTo") Instant occurredTo);
}
