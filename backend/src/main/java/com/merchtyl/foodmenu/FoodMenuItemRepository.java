package com.merchtyl.foodmenu;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface FoodMenuItemRepository extends JpaRepository<FoodMenuItem, UUID> {
    @EntityGraph(attributePaths={"category","product","variants","modifierGroupAssignments","modifierGroupAssignments.group","modifierGroupAssignments.group.options","components"}) List<FoodMenuItem> findAllByStoreIdOrderByCategoryDisplayOrderAscDisplayOrderAscDisplayNameAsc(UUID storeId);
    @EntityGraph(attributePaths={"category","product","variants","modifierGroupAssignments","modifierGroupAssignments.group","modifierGroupAssignments.group.options","components"}) Optional<FoodMenuItem> findByIdAndStoreId(UUID id, UUID storeId);
    boolean existsByStoreIdAndProductIdAndIdNot(UUID storeId, UUID productId, UUID id);
    void deleteByProductId(UUID productId);
}
