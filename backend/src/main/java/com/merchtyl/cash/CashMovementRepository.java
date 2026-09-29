package com.merchtyl.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID>, JpaSpecificationExecutor<CashMovement> {
    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "approvedBy", "reversedMovement"})
    Page<CashMovement> findAll(Specification<CashMovement> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "createdBy", "approvedBy", "reversedMovement"})
    List<CashMovement> findAll(Specification<CashMovement> specification, Sort sort);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "registerSession.businessDay", "createdBy", "approvedBy", "reversedMovement"})
    @Query("select movement from CashMovement movement where movement.id = :id")
    java.util.Optional<CashMovement> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByReversedMovement_Id(UUID movementId);
}
