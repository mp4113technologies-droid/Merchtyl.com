package com.merchtyl.foodmenu;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface FoodMenuItemRepository extends JpaRepository<FoodMenuItem, UUID> {
    @EntityGraph(attributePaths={"category","product"}) List<FoodMenuItem> findAllByStoreIdOrderByCategoryDisplayOrderAscDisplayOrderAscDisplayNameAsc(UUID storeId);
    @EntityGraph(attributePaths={"category","product"}) Optional<FoodMenuItem> findByIdAndStoreId(UUID id, UUID storeId);
    boolean existsByStoreIdAndProductIdAndIdNot(UUID storeId, UUID productId, UUID id);
}
