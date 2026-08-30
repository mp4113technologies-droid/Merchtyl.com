package com.merchtyl.foodmenu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FoodMenuCategoryRepository extends JpaRepository<FoodMenuCategory, UUID> {
    List<FoodMenuCategory> findAllByStoreIdOrderByDisplayOrderAscNameAsc(UUID storeId);
    Optional<FoodMenuCategory> findByIdAndStoreId(UUID id, UUID storeId);
    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(UUID storeId, String name, UUID id);
}
