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

public interface LotteryCommissionRuleRepository extends JpaRepository<LotteryCommissionRule, UUID>, JpaSpecificationExecutor<LotteryCommissionRule> {
    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store"})
    Page<LotteryCommissionRule> findAll(Specification<LotteryCommissionRule> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store"})
    Optional<LotteryCommissionRule> findById(UUID id);

    @Query("""
            select count(rule) > 0
            from LotteryCommissionRule rule
            where rule.operator.id = :operatorId
              and rule.jurisdiction.id = :jurisdictionId
              and rule.store.id = :storeId
              and rule.ruleType = :ruleType
              and rule.status in :blockingStatuses
              and (:excludeId is null or rule.id <> :excludeId)
              and rule.effectiveFrom <= :effectiveTo
              and (rule.effectiveTo is null or rule.effectiveTo >= :effectiveFrom)
            """)
    boolean existsOverlappingRule(
            @Param("operatorId") UUID operatorId,
            @Param("jurisdictionId") UUID jurisdictionId,
            @Param("storeId") UUID storeId,
            @Param("ruleType") LotteryCommissionRuleType ruleType,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("blockingStatuses") Collection<LotteryCommissionRuleStatus> blockingStatuses,
            @Param("excludeId") UUID excludeId);

    @EntityGraph(attributePaths = {"operator", "jurisdiction", "store"})
    @Query("""
            select rule
            from LotteryCommissionRule rule
            where rule.operator.id = :operatorId
              and rule.jurisdiction.id = :jurisdictionId
              and rule.store.id = :storeId
              and rule.status = com.merchtyl.lottery.LotteryCommissionRuleStatus.ACTIVE
              and rule.effectiveFrom <= :periodEnd
              and (rule.effectiveTo is null or rule.effectiveTo >= :periodStart)
            order by rule.effectiveFrom asc, rule.createdAt asc, rule.id asc
            """)
    List<LotteryCommissionRule> findEffectiveSettlementRules(
            @Param("operatorId") UUID operatorId,
            @Param("jurisdictionId") UUID jurisdictionId,
            @Param("storeId") UUID storeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
