package com.merchtyl.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, UUID>, JpaSpecificationExecutor<ProductSupplier> {
    boolean existsByProductIdAndSupplier(UUID productId, Supplier supplier);

    boolean existsByProductIdAndSupplierAndIdNot(UUID productId, Supplier supplier, UUID id);
}
