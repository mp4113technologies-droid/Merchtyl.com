package com.merchtyl.refunds;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID>, JpaSpecificationExecutor<Refund> {
    @Override
    @EntityGraph(attributePaths = {"returnRecord", "originalSale", "store", "register", "registerSession", "createdBy", "approvedBy"})
    Page<Refund> findAll(Specification<Refund> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"returnRecord", "originalSale", "store", "register", "registerSession", "createdBy", "approvedBy"})
    List<Refund> findAll(Specification<Refund> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"returnRecord", "originalSale", "store", "register", "registerSession", "createdBy", "approvedBy"})
    Optional<Refund> findById(UUID id);

    boolean existsByReturnRecord_Id(UUID returnId);

    @EntityGraph(attributePaths = {"returnRecord", "originalSale", "store", "register", "registerSession", "createdBy", "approvedBy"})
    Optional<Refund> findByReturnRecord_Id(UUID returnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"returnRecord", "originalSale", "store", "register", "registerSession", "createdBy", "approvedBy"})
    @Query("select refund from Refund refund where refund.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(item.quantity), 0)
            from Refund refund
            join refund.returnRecord returnRecord
            join returnRecord.items item
            where item.originalSaleItem.id = :saleItemId
            """)
    BigDecimal refundedQuantityForSaleItem(@Param("saleItemId") UUID saleItemId);
}
