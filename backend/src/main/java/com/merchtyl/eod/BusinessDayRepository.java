package com.merchtyl.eod;

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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessDayRepository extends JpaRepository<BusinessDay, UUID>, JpaSpecificationExecutor<BusinessDay> {
    @Override
    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    Page<BusinessDay> findAll(Specification<BusinessDay> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    Optional<BusinessDay> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    @Query("select day from BusinessDay day where day.id = :id")
    Optional<BusinessDay> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    Optional<BusinessDay> findFirstByStore_IdAndStatusInOrderByBusinessDateDescOpenedAtDesc(
            UUID storeId,
            Collection<BusinessDayStatus> statuses);

    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    Optional<BusinessDay> findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(UUID storeId);

    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    Optional<BusinessDay> findByStore_IdAndBusinessDate(UUID storeId, LocalDate businessDate);

    boolean existsByStore_IdAndStatusIn(UUID storeId, Collection<BusinessDayStatus> statuses);

    boolean existsByStore_IdAndBusinessDate(UUID storeId, LocalDate businessDate);

    boolean existsByStore_IdAndBusinessDateGreaterThan(UUID storeId, LocalDate businessDate);

    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    List<BusinessDay> findByStore_IdAndStatusIn(UUID storeId, Collection<BusinessDayStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"store", "openedBy", "closingStartedBy", "closedBy", "reopenedBy"})
    @Query("select day from BusinessDay day where day.store.id = :storeId and day.status in :statuses order by day.businessDate desc, day.openedAt desc")
    List<BusinessDay> findActiveByStoreIdForUpdate(
            @Param("storeId") UUID storeId,
            @Param("statuses") Collection<BusinessDayStatus> statuses);
}
