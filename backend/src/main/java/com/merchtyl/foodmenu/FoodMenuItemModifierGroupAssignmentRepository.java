package com.merchtyl.foodmenu;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FoodMenuItemModifierGroupAssignmentRepository extends JpaRepository<FoodMenuItemModifierGroupAssignment, UUID> {
    List<FoodMenuItemModifierGroupAssignment> findAllByGroup_Id(UUID groupId);
    long countByGroup_IdAndActiveTrue(UUID groupId);
}
