package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotteryPayoutPolicyRepository extends JpaRepository<LotteryPayoutPolicy, UUID>, JpaSpecificationExecutor<LotteryPayoutPolicy> {
    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store"})
    Page<LotteryPayoutPolicy> findAll(Specification<LotteryPayoutPolicy> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store"})
    Optional<LotteryPayoutPolicy> findById(UUID id);

    @Query("""
            select count(policy) > 0
            from LotteryPayoutPolicy policy
            where policy.operator.id = :operatorId
              and policy.jurisdiction.id = :jurisdictionId
              and policy.store.id = :storeId
              and policy.status in :blockingStatuses
              and (:excludeId is null or policy.id <> :excludeId)
              and policy.effectiveFrom <= :effectiveTo
              and (policy.effectiveTo is null or policy.effectiveTo >= :effectiveFrom)
            """)
    boolean existsOverlappingPolicy(
            @Param("operatorId") UUID operatorId,
            @Param("jurisdictionId") UUID jurisdictionId,
            @Param("storeId") UUID storeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("blockingStatuses") Collection<LotteryPayoutPolicyStatus> blockingStatuses,
            @Param("excludeId") UUID excludeId);

    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store"})
    @Query("""
            select policy
            from LotteryPayoutPolicy policy
            where policy.operator.id = :operatorId
              and policy.jurisdiction.id = :jurisdictionId
              and policy.store.id = :storeId
              and policy.status = com.merchtyl.lottery.LotteryPayoutPolicyStatus.ACTIVE
              and policy.effectiveFrom <= :businessDate
              and (policy.effectiveTo is null or policy.effectiveTo >= :businessDate)
            order by policy.effectiveFrom desc, policy.createdAt desc, policy.id desc
            """)
    List<LotteryPayoutPolicy> findEffectivePolicies(
            @Param("operatorId") UUID operatorId,
            @Param("jurisdictionId") UUID jurisdictionId,
            @Param("storeId") UUID storeId,
            @Param("businessDate") LocalDate businessDate,
            Pageable pageable);
}
