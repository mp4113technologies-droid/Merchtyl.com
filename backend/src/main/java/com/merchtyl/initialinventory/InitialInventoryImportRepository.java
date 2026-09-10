package com.merchtyl.initialinventory;
import org.springframework.data.jpa.repository.*; import java.util.*;
public interface InitialInventoryImportRepository extends JpaRepository<InitialInventoryImport,UUID>{
 @EntityGraph(attributePaths="rows") Optional<InitialInventoryImport> findWithRowsById(UUID id);
 boolean existsByTenantIdAndStoreIdAndStatus(UUID tenantId,UUID storeId,InitialInventoryImportStatus status);
}
