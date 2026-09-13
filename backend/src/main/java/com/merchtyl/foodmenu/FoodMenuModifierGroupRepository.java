package com.merchtyl.foodmenu;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FoodMenuModifierGroupRepository extends JpaRepository<FoodMenuModifierGroup, UUID> {
    @EntityGraph(attributePaths = "options")
    List<FoodMenuModifierGroup> findAllByStoreIdOrderByNameAsc(UUID storeId);
    @EntityGraph(attributePaths = "options")
    Optional<FoodMenuModifierGroup> findByIdAndStoreId(UUID id, UUID storeId);
    @EntityGraph(attributePaths = "options")
    Optional<FoodMenuModifierGroup> findFirstByStoreIdAndNameIgnoreCaseOrderByCreatedAtAsc(UUID storeId, String name);
    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(UUID storeId, String name, UUID id);
}
