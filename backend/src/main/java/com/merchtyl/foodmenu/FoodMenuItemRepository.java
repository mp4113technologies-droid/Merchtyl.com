package com.merchtyl.foodmenu;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface FoodMenuItemRepository extends JpaRepository<FoodMenuItem, UUID> {
    @EntityGraph(attributePaths={"category","product","variants","modifierGroups","modifierGroups.options"}) List<FoodMenuItem> findAllByStoreIdOrderByCategoryDisplayOrderAscDisplayOrderAscDisplayNameAsc(UUID storeId);
    @EntityGraph(attributePaths={"category","product","variants","modifierGroups","modifierGroups.options"}) Optional<FoodMenuItem> findByIdAndStoreId(UUID id, UUID storeId);
    boolean existsByStoreIdAndProductIdAndIdNot(UUID storeId, UUID productId, UUID id);
}
