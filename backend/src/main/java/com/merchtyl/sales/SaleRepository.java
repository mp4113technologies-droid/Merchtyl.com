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
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID>, JpaSpecificationExecutor<Sale> {
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
