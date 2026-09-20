package com.merchtyl.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "completedBy", "items"})
    @Query("select sale from Sale sale where sale.store.id = :storeId and sale.phoneConfirmedAt is not null "
            + "and sale.status <> com.merchtyl.sales.SaleStatus.CANCELLED "
            + "and sale.kitchenStatus in :statuses order by sale.pickupAt asc, sale.id asc")
    List<Sale> findActivePhoneOrders(@Param("storeId") UUID storeId,
                                     @Param("statuses") List<KitchenOrderStatus> statuses);

    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "completedBy", "items"})
    @Query("select sale from Sale sale where sale.store.id = :storeId and sale.phoneConfirmedAt is not null "
            + "and sale.businessDate = :businessDate and sale.kitchenStatus = com.merchtyl.sales.KitchenOrderStatus.COMPLETED "
            + "order by sale.kitchenUpdatedAt desc, sale.id desc")
    List<Sale> findCompletedPhoneOrders(@Param("storeId") UUID storeId,
                                        @Param("businessDate") LocalDate businessDate);
    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "completedBy"})
    Page<Sale> findAll(Specification<Sale> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "completedBy", "items", "items.product", "items.product.category"})
    List<Sale> findAll(Specification<Sale> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "completedBy"})
    Optional<Sale> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "completedBy", "items"})
    @Query("select sale from Sale sale where sale.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") UUID id);
}
