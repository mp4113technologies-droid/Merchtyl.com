package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.platform.admin.PlatformAdministrationService;
import com.merchtyl.platform.admin.TenantStatus;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StoreAccessService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final StoreRepository storeRepository;
    private final UserStoreAssignmentRepository assignmentRepository;
    private final AuditService auditService;
    private final PlatformAdministrationService platformAdministrationService;

    public StoreAccessService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            StoreRepository storeRepository,
            UserStoreAssignmentRepository assignmentRepository,
            AuditService auditService,
            PlatformAdministrationService platformAdministrationService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.storeRepository = storeRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
        this.platformAdministrationService = platformAdministrationService;
    }

    @Transactional(readOnly = true)
    public User currentTenantUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Authenticated tenant user is required");
        }
        if (authentication.getAuthorities().stream().noneMatch(authority -> AuthorizationService.TENANT_SCOPE_AUTHORITY.equals(authority.getAuthority()))) {
            throw new ForbiddenOperationException("Tenant account scope is required");
        }
        User actor = userRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(candidate -> candidate.isEnabled() && !candidate.isLocked())
                .orElseThrow(() -> new ForbiddenOperationException("Authenticated tenant user is required"));
        requireTenantActive(actor);
        return actor;
    }

    @Transactional(readOnly = true)
    public UUID currentTenantId(Authentication authentication) {
        UUID tenantId = currentTenantUser(authentication).getTenantId();
        if (tenantId == null) {
            throw new ForbiddenOperationException("Authenticated user is not assigned to a tenant");
        }
        return tenantId;
    }

    @Transactional(readOnly = true)
    public boolean canAccessStore(UUID userId, UUID storeId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || user.isLocked() || user.getTenantId() == null) {
            return false;
        }
        if (!isTenantActive(user.getTenantId())) {
            return false;
        }
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null || store.getTenantId() == null || !store.getTenantId().equals(user.getTenantId())) {
            return false;
        }
        Set<RoleName> roles = roles(user);
        if (isOwner(roles)) {
            return true;
        }
        return assignmentRepository.existsByTenantIdAndUser_IdAndStore_IdAndActiveTrue(
                user.getTenantId(),
                user.getId(),
                storeId);
    }

    @Transactional(readOnly = true)
    public boolean canManageStore(UUID userId, UUID storeId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || user.isLocked() || user.getTenantId() == null) {
            return false;
        }
        if (!isTenantActive(user.getTenantId())) {
            return false;
        }
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null || store.getTenantId() == null || !store.getTenantId().equals(user.getTenantId())) {
            return false;
        }
        Set<RoleName> roles = roles(user);
        if (isOwner(roles)) {
            return true;
        }
        return assignmentRepository.existsByTenantIdAndUser_IdAndStore_IdAndAssignmentRoleAndActiveTrue(
                user.getTenantId(),
                user.getId(),
                storeId,
                AssignmentRole.MANAGER);
    }

    @Transactional(readOnly = true)
    public void requireStoreAccess(Authentication authentication, UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        User actor = currentTenantUser(authentication);
        if (!canAccessStore(actor.getId(), storeId)) {
            auditDenied(actor, storeId, "User is not assigned to this store");
            throw new ForbiddenOperationException("User is not assigned to this store");
        }
    }

    @Transactional(readOnly = true)
    public void requireStoreManagement(Authentication authentication, UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        User actor = currentTenantUser(authentication);
        if (!canManageStore(actor.getId(), storeId)) {
            auditDenied(actor, storeId, "User cannot manage this store");
            throw new ForbiddenOperationException("User cannot manage this store");
        }
    }

    @Transactional(readOnly = true)
    public User requireProductManagementScope(Authentication authentication, Set<UUID> requestedStoreIds) {
        User actor = currentTenantUser(authentication);
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isOwner(actorRoles)) {
            requestedStoreIds.forEach(storeId -> tenantStore(actor.getTenantId(), storeId));
            return actor;
        }
        Set<UUID> managedStoreIds = getActiveManagedStoreIds(actor.getId());
        if (!StoreAccessService.isManager(actorRoles) || !managedStoreIds.containsAll(requestedStoreIds)) {
            requestedStoreIds.stream()
                    .filter(storeId -> !managedStoreIds.contains(storeId))
                    .forEach(storeId -> auditDenied(actor, storeId, "PRODUCT_STORE_ACCESS_DENIED"));
            throw new ForbiddenOperationException("PRODUCT_STORE_ACCESS_DENIED");
        }
        return actor;
    }

    @Transactional(readOnly = true)
    public Set<UUID> getActiveAssignedStoreIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return assignmentRepository.findByTenantIdAndUserAndActiveTrue(user.getTenantId(), user).stream()
                .map(assignment -> assignment.getStore().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public Set<UUID> getActiveManagedStoreIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return assignmentRepository.findByTenantIdAndUserAndActiveTrue(user.getTenantId(), user).stream()
                .filter(assignment -> assignment.getAssignmentRole() == AssignmentRole.MANAGER)
                .map(assignment -> assignment.getStore().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public List<AssignedStoreResponse> assignedStores(Authentication authentication) {
        User actor = currentTenantUser(authentication);
        Set<RoleName> roles = roles(actor);
        if (isOwner(roles)) {
            return storeRepository.findByTenantIdAndActiveTrueOrderByNameAscIdAsc(actor.getTenantId()).stream()
                    .map(store -> AssignedStoreResponse.from(store, AssignmentRole.MANAGER))
                    .toList();
        }
        return assignmentRepository.findByTenantIdAndUserAndActiveTrue(actor.getTenantId(), actor).stream()
                .filter(assignment -> !isManager(roles) || assignment.getAssignmentRole() == AssignmentRole.MANAGER)
                .filter(assignment -> assignment.getStore().isActive())
                .sorted(Comparator.comparing((UserStoreAssignment assignment) -> assignment.getStore().getName())
                        .thenComparing(assignment -> assignment.getStore().getId()))
                .map(assignment -> AssignedStoreResponse.from(assignment.getStore(), assignment.getAssignmentRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Store tenantStore(UUID tenantId, UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findByIdAndTenantId(storeId, tenantId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    @Transactional(readOnly = true)
    public Set<RoleName> roles(User user) {
        return userRoleRepository.findByUser(user).stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isOwner(Set<RoleName> roles) {
        return roles.contains(RoleName.TENANT_OWNER) || roles.contains(RoleName.OWNER);
    }

    public static boolean isManager(Set<RoleName> roles) {
        return roles.contains(RoleName.STORE_MANAGER) || roles.contains(RoleName.MANAGER);
    }

    public static boolean isCashier(Set<RoleName> roles) {
        return roles.contains(RoleName.CASHIER);
    }

    private void auditDenied(User actor, UUID storeId, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.UNAUTHORIZED_STORE_ACCESS_DENIED,
                "STORE",
                storeId,
                storeId,
                null,
                null,
                null,
                reason));
    }

    private void requireTenantActive(User user) {
        if (!isTenantActive(user.getTenantId())) {
            throw new ForbiddenOperationException("Merchant access is suspended or closed");
        }
    }

    private boolean isTenantActive(UUID tenantId) {
        return platformAdministrationService.tenantStatus(tenantId)
                .map(status -> status != TenantStatus.SUSPENDED && status != TenantStatus.CLOSED && status != TenantStatus.REJECTED)
                .orElse(false);
    }
}
