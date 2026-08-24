package com.merchtyl.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, UUID> {
    @Query("select coalesce(sum(item.quantity), 0) from ReturnItem item where item.originalSaleItem.id = :saleItemId")
    BigDecimal returnedQuantityForSaleItem(@Param("saleItemId") UUID saleItemId);

    @Query("select coalesce(sum(item.returnSubtotalAmount), 0) from ReturnItem item where item.originalSaleItem.id = :saleItemId")
    BigDecimal returnedSubtotalForSaleItem(@Param("saleItemId") UUID saleItemId);

    @Query("select coalesce(sum(item.returnTaxAmount), 0) from ReturnItem item where item.originalSaleItem.id = :saleItemId")
    BigDecimal returnedTaxForSaleItem(@Param("saleItemId") UUID saleItemId);

    @Query("select coalesce(sum(item.returnTotalAmount), 0) from ReturnItem item where item.originalSaleItem.id = :saleItemId")
    BigDecimal returnedTotalForSaleItem(@Param("saleItemId") UUID saleItemId);
}
