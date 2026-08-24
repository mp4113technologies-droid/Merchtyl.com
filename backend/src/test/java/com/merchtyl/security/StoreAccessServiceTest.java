package com.merchtyl.security;

import com.merchtyl.audit.AuditService;
import com.merchtyl.platform.admin.PlatformAdministrationService;
import com.merchtyl.platform.admin.TenantStatus;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreAccessServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID OTHER_STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final UserStoreAssignmentRepository assignmentRepository = mock(UserStoreAssignmentRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PlatformAdministrationService platformAdministrationService = mock(PlatformAdministrationService.class);
    private final StoreAccessService service = new StoreAccessService(
            userRepository,
            userRoleRepository,
            storeRepository,
            assignmentRepository,
            auditService,
            platformAdministrationService);

    @Test
    void ownerCanAccessAnyStoreInOwnTenant() {
        User owner = tenantUser("owner@example.local");
        Store store = tenantStore(STORE_ID, TENANT_ID);
        when(platformAdministrationService.tenantStatus(TENANT_ID)).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(userRoleRepository.findByUser(owner)).thenReturn(List.of(new UserRole(owner, new Role(RoleName.TENANT_OWNER, "Owner", true))));

        assertThat(service.canAccessStore(owner.getId(), STORE_ID)).isTrue();
        assertThat(service.canManageStore(owner.getId(), STORE_ID)).isTrue();
    }

    @Test
    void managerCanAccessOnlyActiveAssignedStores() {
        User manager = tenantUser("manager@example.local");
        Store assignedStore = tenantStore(STORE_ID, TENANT_ID);
        Store otherStore = tenantStore(OTHER_STORE_ID, TENANT_ID);
        when(platformAdministrationService.tenantStatus(TENANT_ID)).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(assignedStore));
        when(storeRepository.findById(OTHER_STORE_ID)).thenReturn(Optional.of(otherStore));
        when(userRoleRepository.findByUser(manager)).thenReturn(List.of(new UserRole(manager, new Role(RoleName.STORE_MANAGER, "Manager", true))));
        when(assignmentRepository.existsByTenantIdAndUser_IdAndStore_IdAndActiveTrue(TENANT_ID, manager.getId(), STORE_ID)).thenReturn(true);
        when(assignmentRepository.existsByTenantIdAndUser_IdAndStore_IdAndAssignmentRoleAndActiveTrue(
                TENANT_ID,
                manager.getId(),
                STORE_ID,
                AssignmentRole.MANAGER)).thenReturn(true);

        assertThat(service.canAccessStore(manager.getId(), STORE_ID)).isTrue();
        assertThat(service.canManageStore(manager.getId(), STORE_ID)).isTrue();
        assertThat(service.canAccessStore(manager.getId(), OTHER_STORE_ID)).isFalse();
        assertThat(service.canManageStore(manager.getId(), OTHER_STORE_ID)).isFalse();
    }

    @Test
    void crossTenantStoreNeverGrantsAccess() {
        User cashier = tenantUser("cashier@example.local");
        Store otherTenantStore = tenantStore(STORE_ID, UUID.fromString("00000000-0000-0000-0000-000000000999"));
        when(platformAdministrationService.tenantStatus(TENANT_ID)).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(userRepository.findById(cashier.getId())).thenReturn(Optional.of(cashier));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(otherTenantStore));
        when(userRoleRepository.findByUser(cashier)).thenReturn(List.of(new UserRole(cashier, new Role(RoleName.CASHIER, "Cashier", true))));

        assertThat(service.canAccessStore(cashier.getId(), STORE_ID)).isFalse();
        assertThat(service.canManageStore(cashier.getId(), STORE_ID)).isFalse();
    }

    @Test
    void managerProductScopeRejectsEntireRequestContainingUnassignedStore() {
        User manager = tenantUser("manager@example.local");
        Store assignedStore = tenantStore(STORE_ID, TENANT_ID);
        UserStoreAssignment assignment = new UserStoreAssignment(
                TENANT_ID, manager, assignedStore, AssignmentRole.MANAGER, manager.getId());
        var authentication = new UsernamePasswordAuthenticationToken(
                manager.getEmail(), "n/a", List.of(new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY)));
        when(platformAdministrationService.tenantStatus(TENANT_ID)).thenReturn(Optional.of(TenantStatus.ACTIVE));
        when(userRepository.findByEmailIgnoreCase(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(userRoleRepository.findByUser(manager)).thenReturn(List.of(
                new UserRole(manager, new Role(RoleName.STORE_MANAGER, "Manager", true))));
        when(assignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, manager)).thenReturn(List.of(assignment));

        assertThatThrownBy(() -> service.requireProductManagementScope(
                authentication, Set.of(STORE_ID, OTHER_STORE_ID)))
                .isInstanceOf(com.merchtyl.common.ForbiddenOperationException.class)
                .hasMessage("PRODUCT_STORE_ACCESS_DENIED");
    }

    private static User tenantUser(String email) {
        User user = new User(email, email, "hash");
        user.assignTenant(TENANT_ID);
        return user;
    }

    private static Store tenantStore(UUID id, UUID tenantId) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(id);
        when(store.getTenantId()).thenReturn(tenantId);
        return store;
    }
}
