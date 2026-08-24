package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {
    boolean existsByBarcodeIgnoreCase(String barcode);
    boolean existsByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);
    boolean existsByTenantIdAndBarcodeIgnoreCaseAndProductIdNot(UUID tenantId, String barcode, UUID productId);

    boolean existsByBarcodeIgnoreCaseAndProductIdNot(String barcode, UUID productId);

    @EntityGraph(attributePaths = {"product", "product.unitOfMeasure", "product.category", "product.brand", "variant"})
    Optional<ProductBarcode> findByBarcodeIgnoreCase(String barcode);
    @EntityGraph(attributePaths = {"product", "product.unitOfMeasure", "product.category", "product.brand", "variant"})
    Optional<ProductBarcode> findByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);
}
