package com.merchtyl.lottery;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotteryPayoutRepository extends JpaRepository<LotteryPayout, UUID>, JpaSpecificationExecutor<LotteryPayout> {
    @Override
    @EntityGraph(attributePaths = {
            "operator", "policy", "store", "register", "device", "cashier", "registerSession",
            "validatedBy", "authorizedBy", "paidBy", "rejectedBy"
    })
    Page<LotteryPayout> findAll(Specification<LotteryPayout> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "operator", "policy", "store", "register", "device", "cashier", "registerSession",
            "validatedBy", "authorizedBy", "paidBy", "rejectedBy"
    })
    List<LotteryPayout> findAll(Specification<LotteryPayout> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {
            "operator", "policy", "store", "register", "device", "cashier", "registerSession",
            "validatedBy", "authorizedBy", "paidBy", "rejectedBy"
    })
    Optional<LotteryPayout> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "operator", "policy", "store", "register", "device", "cashier", "registerSession",
            "validatedBy", "authorizedBy", "paidBy", "rejectedBy", "approvals"
    })
    @Query("select payout from LotteryPayout payout where payout.id = :id")
    Optional<LotteryPayout> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select sum(payout.amount)
            from LotteryPayout payout
            where payout.registerSession.id = :registerSessionId
              and payout.payoutMethod = com.merchtyl.lottery.LotteryPayoutMethod.CASH
              and payout.status = com.merchtyl.lottery.LotteryPayoutStatus.AUTHORIZED
              and (:excludePayoutId is null or payout.id <> :excludePayoutId)
            """)
    BigDecimal sumReservedCashObligations(
            @Param("registerSessionId") UUID registerSessionId,
            @Param("excludePayoutId") UUID excludePayoutId);

    @Query("""
            select coalesce(sum(payout.amount), 0)
            from LotteryPayout payout
            where payout.operator.id = :operatorId
              and payout.store.id = :storeId
              and payout.status in :statuses
              and payout.businessDate >= :periodStart
              and payout.businessDate <= :periodEnd
            """)
    BigDecimal sumSettlementPayouts(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("statuses") Collection<LotteryPayoutStatus> statuses,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Query("""
            select count(payout)
            from LotteryPayout payout
            where payout.operator.id = :operatorId
              and payout.store.id = :storeId
              and payout.status in :statuses
              and payout.businessDate >= :periodStart
              and payout.businessDate <= :periodEnd
            """)
    long countSettlementPayouts(
            @Param("operatorId") UUID operatorId,
            @Param("storeId") UUID storeId,
            @Param("statuses") Collection<LotteryPayoutStatus> statuses,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
