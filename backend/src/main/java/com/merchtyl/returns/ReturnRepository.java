package com.merchtyl.returns;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReturnRepository extends JpaRepository<Return, UUID>, JpaSpecificationExecutor<Return> {
    @Override
    @EntityGraph(attributePaths = {"originalSale", "store", "register", "registerSession", "createdBy"})
    Page<Return> findAll(Specification<Return> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"originalSale", "store", "register", "registerSession", "createdBy"})
    Optional<Return> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"originalSale", "store", "register", "registerSession", "createdBy", "items"})
    @Query("select returnRecord from MerchtylReturn returnRecord where returnRecord.id = :id")
    Optional<Return> findByIdForUpdate(@Param("id") UUID id);
}
