package com.merchtyl.registersession;

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
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface RegisterSessionRepository extends JpaRepository<RegisterSession, UUID>, JpaSpecificationExecutor<RegisterSession> {
    @Override
    @EntityGraph(attributePaths = {"store", "register", "device", "assignedCashier", "openedBy", "closedBy"})
    Page<RegisterSession> findAll(Specification<RegisterSession> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"store", "register", "device", "assignedCashier", "openedBy", "closedBy"})
    List<RegisterSession> findAll(Specification<RegisterSession> specification, Sort sort);

    boolean existsByRegister_IdAndStatusIn(UUID registerId, Collection<RegisterSessionStatus> statuses);

    @EntityGraph(attributePaths = {"store", "register", "device", "assignedCashier", "openedBy", "closedBy"})
    Optional<RegisterSession> findFirstByRegister_IdAndStatusInOrderByOpenedAtDesc(
            UUID registerId,
            Collection<RegisterSessionStatus> statuses);

    @EntityGraph(attributePaths = {"store", "register", "businessDay", "device", "assignedCashier", "openedBy", "closedBy"})
    Optional<RegisterSession> findFirstByDevice_IdAndStatusInOrderByOpenedAtDesc(UUID deviceId, Collection<RegisterSessionStatus> statuses);

    @EntityGraph(attributePaths = {"store", "register", "businessDay", "device", "assignedCashier", "openedBy", "closedBy"})
    Optional<RegisterSession> findFirstByDevice_DeviceIdentifierIgnoreCaseAndStatusInOrderByOpenedAtDesc(String deviceIdentifier, Collection<RegisterSessionStatus> statuses);

    @EntityGraph(attributePaths = {"store", "register", "businessDay", "device", "assignedCashier", "openedBy", "closedBy"})
    Optional<RegisterSession> findFirstByAssignedCashier_IdAndStatusInOrderByOpenedAtDesc(UUID assignedCashierId, Collection<RegisterSessionStatus> statuses);

    boolean existsByDevice_IdAndStatusIn(UUID deviceId, Collection<RegisterSessionStatus> statuses);

    Optional<RegisterSession> findFirstByDevice_IdAndStatusOrderByOpenedAtDesc(UUID deviceId, RegisterSessionStatus status);

    Optional<RegisterSession> findFirstByDevice_DeviceIdentifierIgnoreCaseAndStatusOrderByOpenedAtDesc(
            String deviceIdentifier,
            RegisterSessionStatus status);

    Optional<RegisterSession> findFirstByAssignedCashier_IdAndStatusOrderByOpenedAtDesc(
            UUID assignedCashierId,
            RegisterSessionStatus status);

    boolean existsByAssignedCashier_IdAndStatusIn(UUID assignedCashierId, Collection<RegisterSessionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RegisterSession session where session.id = :id")
    Optional<RegisterSession> findByIdForUpdate(@Param("id") UUID id);
}
