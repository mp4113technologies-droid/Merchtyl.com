package com.merchtyl.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID>, JpaSpecificationExecutor<TaxRule> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    @Query("""
            select rule
            from TaxRule rule
            where rule.active = true
              and rule.effectiveFrom <= :transactionDate
              and (rule.effectiveTo is null or rule.effectiveTo >= :transactionDate)
            order by rule.priority asc, rule.code asc, rule.id asc
            """)
    List<TaxRule> findActiveEffectiveRules(@Param("transactionDate") LocalDate transactionDate);
}
