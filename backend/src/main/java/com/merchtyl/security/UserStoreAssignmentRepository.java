package com.merchtyl.security;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStoreAssignmentRepository extends JpaRepository<UserStoreAssignment, UUID> {
    @EntityGraph(attributePaths = {"store"})
    List<UserStoreAssignment> findByUser(User user);

    @EntityGraph(attributePaths = {"store"})
    List<UserStoreAssignment> findByUserIn(Collection<User> users);

    @EntityGraph(attributePaths = {"store"})
    List<UserStoreAssignment> findByTenantIdAndUserAndActiveTrue(UUID tenantId, User user);

    @EntityGraph(attributePaths = {"store", "user"})
    List<UserStoreAssignment> findByTenantIdAndUserInAndActiveTrue(UUID tenantId, Collection<User> users);

    @EntityGraph(attributePaths = {"store", "user"})
    List<UserStoreAssignment> findByTenantIdAndStore_IdAndActiveTrue(UUID tenantId, UUID storeId);

    Optional<UserStoreAssignment> findByTenantIdAndUser_IdAndStore_IdAndAssignmentRole(
            UUID tenantId,
            UUID userId,
            UUID storeId,
            AssignmentRole assignmentRole);

    List<UserStoreAssignment> findByTenantIdAndUser_Id(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUser_IdAndStore_IdAndAssignmentRoleAndActiveTrue(
            UUID tenantId,
            UUID userId,
            UUID storeId,
            AssignmentRole assignmentRole);

    boolean existsByTenantIdAndUser_IdAndStore_IdAndActiveTrue(UUID tenantId, UUID userId, UUID storeId);

    void deleteByUser(User user);
}
