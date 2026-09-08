package com.merchtyl.discount;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.jpa.repository.Query;

public interface DiscountDefinitionRepository extends JpaRepository<DiscountDefinition, UUID> {
    List<DiscountDefinition> findAllByTenantIdOrderByNameAsc(UUID tenantId);
    List<DiscountDefinition> findAllByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);
    Optional<DiscountDefinition> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
    @Query("select count(s) > 0 from Sale s where s.discountDefinitionId = :id") boolean hasHistoricalUsage(UUID id);
}
