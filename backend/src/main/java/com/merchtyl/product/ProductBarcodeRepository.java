package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {
    java.util.List<ProductBarcode> findAllByTenantId(UUID tenantId);
    boolean existsByBarcodeIgnoreCase(String barcode);
    boolean existsByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);
    boolean existsByTenantIdAndBarcodeIgnoreCaseAndProductIdNot(UUID tenantId, String barcode, UUID productId);

    boolean existsByBarcodeIgnoreCaseAndProductIdNot(String barcode, UUID productId);

    @EntityGraph(attributePaths = {"product", "product.unitOfMeasure", "product.category", "product.brand", "variant"})
    Optional<ProductBarcode> findByBarcodeIgnoreCase(String barcode);
    @EntityGraph(attributePaths = {"product", "product.unitOfMeasure", "product.category", "product.brand", "variant"})
    Optional<ProductBarcode> findByTenantIdAndBarcodeIgnoreCase(UUID tenantId, String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"product", "variant"})
    @Query("select b from ProductBarcode b where b.id = :id and b.tenantId = :tenantId")
    Optional<ProductBarcode> findOwnedByIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @EntityGraph(attributePaths = {"product", "variant"})
    @Query("select b from ProductBarcode b where b.tenantId = :tenantId and lower(b.barcode) in :barcodes")
    List<ProductBarcode> findOwnedInBulk(@Param("tenantId") UUID tenantId,
                                         @Param("barcodes") Collection<String> normalizedLowercaseBarcodes);
}
