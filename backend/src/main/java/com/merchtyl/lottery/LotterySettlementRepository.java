package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotterySettlementRepository extends JpaRepository<LotterySettlement, UUID>, JpaSpecificationExecutor<LotterySettlement> {
    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store", "approvedBy", "postedBy", "reopenedBy"})
    Page<LotterySettlement> findAll(Specification<LotterySettlement> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store", "approvedBy", "postedBy", "reopenedBy"})
    List<LotterySettlement> findAll(Specification<LotterySettlement> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store", "approvedBy", "postedBy", "reopenedBy"})
    Optional<LotterySettlement> findById(UUID id);

    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store", "approvedBy", "postedBy", "reopenedBy"})
    Optional<LotterySettlement> findByOperator_IdAndStore_IdAndPeriodStartAndPeriodEnd(
            UUID operatorId,
            UUID storeId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
