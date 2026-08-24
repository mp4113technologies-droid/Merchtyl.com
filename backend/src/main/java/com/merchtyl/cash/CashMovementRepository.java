package com.merchtyl.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID>, JpaSpecificationExecutor<CashMovement> {
    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "approvedBy"})
    Page<CashMovement> findAll(Specification<CashMovement> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "approvedBy"})
    List<CashMovement> findAll(Specification<CashMovement> specification, Sort sort);
}
