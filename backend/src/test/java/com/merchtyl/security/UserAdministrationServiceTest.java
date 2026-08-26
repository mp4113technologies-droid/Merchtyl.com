package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.auth.PasswordPolicyException;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_A2_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final UserStoreAssignmentRepository userStoreAssignmentRepository = mock(UserStoreAssignmentRepository.class);
    private final UserRegisterAssignmentRepository userRegisterAssignmentRepository = mock(UserRegisterAssignmentRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final RegisterRepository registerRepository = mock(RegisterRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuditService auditService = mock(AuditService.class);
    private final UserAdministrationService service = new UserAdministrationService(
            userRepository,
            roleRepository,
            userRoleRepository,
            userStoreAssignmentRepository,
            userRegisterAssignmentRepository,
            storeRepository,
            registerRepository,
            passwordEncoder,
            auditService);

    @Test
    void createAndAdminResetUseGlobalPasswordPolicy() {
        assertThatThrownBy(() -> service.create(new UserCreateRequest(
                "cashier@example.local", "Cashier", "test123", List.of(RoleName.CASHIER),
                List.of(STORE_ID), List.of(), true, false), mock(Authentication.class)))
                .isInstanceOf(PasswordPolicyException.class);

        assertThatThrownBy(() -> service.resetPassword(UUID.randomUUID(),
                new UserPasswordResetRequest("VeryLongPasswordForMerchtyl@12345", 1L), mock(Authentication.class)))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    void omittedUuidFilterDoesNotBecomeIsNullPredicate() {
        Object specification = ReflectionTestUtils.invokeMethod(
                UserAdministrationService.class, "equalUuid", "createdByUserId", null);

        assertThat(specification).isNull();
    }

    @Test
    void createUserHashesPasswordAssignsAccessAndAuditsWithoutPassword() {
        Role cashier = new Role(RoleName.CASHIER, "Cashier", true);
        Role ownerRole = new Role(RoleName.TENANT_OWNER, "Owner", true);
        Store store = mock(Store.class);
        Register register = mock(Register.class);
        User actor = new User("owner@example.local", "Owner", "hash");
        actor.assignTenant(TENANT_ID);
        Authentication authentication = mock(Authentication.class);

        when(authentication.getName()).thenReturn("owner@example.local");
        when(userRepository.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmailIgnoreCase("cashier@example.local")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName(RoleName.CASHIER)).thenReturn(Optional.of(cashier));
        when(store.getId()).thenReturn(STORE_ID);
        when(store.getTenantId()).thenReturn(TENANT_ID);
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main");
        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getStore()).thenReturn(store);
        when(storeRepository.findByIdAndTenantId(STORE_ID, TENANT_ID)).thenReturn(Optional.of(store));
        when(registerRepository.findById(REGISTER_ID)).thenReturn(Optional.of(register));
        when(userRoleRepository.findByUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user == actor ? List.of(new UserRole(actor, ownerRole)) : List.of(new UserRole(user, cashier));
        });
        when(userStoreAssignmentRepository.findByTenantIdAndUser_Id(any(), any())).thenAnswer(invocation -> {
            User assignedUser = new User("cashier@example.local", "Cashier User", "hash");
            assignedUser.assignTenant(TENANT_ID);
            return List.of(new UserStoreAssignment(TENANT_ID, assignedUser, store, AssignmentRole.CASHIER, actor.getId()));
        });
        when(userStoreAssignmentRepository.findByTenantIdAndUser_IdAndStore_IdAndAssignmentRole(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(userRegisterAssignmentRepository.findByUser(any(User.class))).thenAnswer(invocation -> List.of(new UserRegisterAssignment(invocation.getArgument(0), register)));

        UserResponse response = service.create(new UserCreateRequest(
                " Cashier@Example.Local ",
                " Cashier User ",
                "CashierDev!2026",
                List.of(RoleName.CASHIER),
                List.of(STORE_ID),
                List.of(REGISTER_ID),
                true,
                false), authentication);

        assertThat(response.email()).isEqualTo("cashier@example.local");
        assertThat(response.displayName()).isEqualTo("Cashier User");
        assertThat(response.roles()).containsExactly(RoleName.CASHIER);
        assertThat(response.storeIds()).containsExactly(STORE_ID);
        assertThat(response.registerIds()).containsExactly(REGISTER_ID);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertThat(passwordEncoder.matches("CashierDev!2026", savedUser.getValue().getPasswordHash())).isTrue();
        assertThat(savedUser.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(savedUser.getValue().getCreatedByUserId()).isEqualTo(actor.getId());
        assertThat(savedUser.getValue().getCreatedByRole()).isEqualTo(RoleName.TENANT_OWNER);

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService, atLeastOnce()).record(audit.capture());
        CreateAuditRecordCommand createdAudit = audit.getAllValues().stream()
                .filter(command -> command.action() == AuditAction.USER_CREATED)
                .findFirst()
                .orElseThrow();
        assertThat(createdAudit.actorUserId()).isEqualTo(actor.getId());
        assertThat(createdAudit.afterSnapshot().toString())
                .contains("cashier@example.local")
                .doesNotContain("CashierDev!2026")
                .doesNotContain(savedUser.getValue().getPasswordHash());
    }

    @Test
    void createUserRejectsDuplicateEmailBeforeSaving() {
        when(userRepository.existsByEmailIgnoreCase("cashier@example.local")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new UserCreateRequest(
                "cashier@example.local",
                "Cashier User",
                "CashierDev!2026",
                List.of(RoleName.CASHIER),
                List.of(),
                List.of(),
                true,
                false), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).saveAndFlush(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void roleAndAssignmentUpdateRejectsRegisterOutsideAssignedStores() {
        User user = new User("manager@example.local", "Manager", "hash");
        user.assignTenant(TENANT_ID);
        User actor = new User("owner@example.local", "Owner", "hash");
        actor.assignTenant(TENANT_ID);
        Role ownerRole = new Role(RoleName.TENANT_OWNER, "Owner", true);
        Role manager = new Role(RoleName.STORE_MANAGER, "Manager", true);
        Store assignedStore = mock(Store.class);
        Store otherStore = mock(Store.class);
        Register register = mock(Register.class);
        UUID otherStoreId = UUID.fromString("00000000-0000-0000-0000-000000000903");
        Authentication authentication = mock(Authentication.class);

        when(authentication.getName()).thenReturn("owner@example.local");
        when(userRepository.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(actor));
        when(userRepository.findByIdAndTenantId(user.getId(), TENANT_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.STORE_MANAGER)).thenReturn(Optional.of(manager));
        when(assignedStore.getId()).thenReturn(STORE_ID);
        when(assignedStore.getTenantId()).thenReturn(TENANT_ID);
        when(otherStore.getId()).thenReturn(otherStoreId);
        when(register.getStore()).thenReturn(otherStore);
        when(storeRepository.findByIdAndTenantId(STORE_ID, TENANT_ID)).thenReturn(Optional.of(assignedStore));
        when(registerRepository.findById(REGISTER_ID)).thenReturn(Optional.of(register));
        when(userRoleRepository.findByUser(any(User.class))).thenAnswer(invocation -> {
            User requested = invocation.getArgument(0);
            return requested == actor ? List.of(new UserRole(actor, ownerRole)) : List.of(new UserRole(user, manager));
        });
        when(userStoreAssignmentRepository.findByTenantIdAndUser_Id(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceRolesAndAssignments(user.getId(), new UserRolesRequest(
                List.of(RoleName.MANAGER),
                List.of(STORE_ID),
                List.of(REGISTER_ID),
                user.getVersion()), authentication))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("registerIds must belong");

        verify(auditService, never()).record(any());
    }

    @Test
    void cashierCannotAccessAssignableStores() {
        User cashier = tenantUser("cashier@example.local", "Cashier");
        Role cashierRole = new Role(RoleName.CASHIER, "Cashier", true);
        Authentication authentication = authentication("cashier@example.local",
                AuthorizationService.TENANT_SCOPE_AUTHORITY,
                "STORE_ACCESS");

        when(userRepository.findByEmailIgnoreCase("cashier@example.local")).thenReturn(Optional.of(cashier));
        when(userRoleRepository.findByUser(cashier)).thenReturn(List.of(new UserRole(cashier, cashierRole)));

        assertThatThrownBy(() -> service.assignableStores(authentication))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Cashiers cannot access assignable stores");
    }

    @Test
    void managerMixedAuthorizedAndUnauthorizedStoreAssignmentFailsBeforeSaving() {
        User manager = tenantUser("manager@example.local", "Manager");
        User cashier = tenantUser("cashier@example.local", "Cashier");
        Role managerRole = new Role(RoleName.STORE_MANAGER, "Manager", true);
        Role cashierRole = new Role(RoleName.CASHIER, "Cashier", true);
        Store storeA1 = store(STORE_ID, "A1");
        Store storeA2 = store(STORE_A2_ID, "A2");
        Authentication authentication = authentication("manager@example.local",
                AuthorizationService.TENANT_SCOPE_AUTHORITY,
                "USER_ASSIGN_STORE");

        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(manager));
        when(userRepository.findByIdAndTenantId(cashier.getId(), TENANT_ID)).thenReturn(Optional.of(cashier));
        when(userRoleRepository.findByUser(manager)).thenReturn(List.of(new UserRole(manager, managerRole)));
        when(userRoleRepository.findByUser(cashier)).thenReturn(List.of(new UserRole(cashier, cashierRole)));
        when(userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, manager))
                .thenReturn(List.of(new UserStoreAssignment(TENANT_ID, manager, storeA1, AssignmentRole.MANAGER, manager.getId())));
        when(userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, cashier))
                .thenReturn(List.of(new UserStoreAssignment(TENANT_ID, cashier, storeA1, AssignmentRole.CASHIER, manager.getId())));
        when(storeRepository.findByIdAndTenantId(STORE_ID, TENANT_ID)).thenReturn(Optional.of(storeA1));
        when(storeRepository.findByIdAndTenantId(STORE_A2_ID, TENANT_ID)).thenReturn(Optional.of(storeA2));

        assertThatThrownBy(() -> service.addStoreAssignments(cashier.getId(), new UserStoreAssignmentRequest(
                List.of(STORE_ID, STORE_A2_ID),
                AssignmentRole.CASHIER,
                null), authentication))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("outside the manager's assignment scope");

        verify(userStoreAssignmentRepository, never()).save(any());
    }

    @Test
    void managerCreatedUserTransferredOutsideManagerScopeIsHidden() {
        User manager = tenantUser("manager@example.local", "Manager");
        User cashier = tenantUser("cashier@example.local", "Cashier");
        cashier.markCreatedBy(manager.getId(), RoleName.STORE_MANAGER);
        Role managerRole = new Role(RoleName.STORE_MANAGER, "Manager", true);
        Role cashierRole = new Role(RoleName.CASHIER, "Cashier", true);
        Store storeA1 = store(STORE_ID, "A1");
        Store storeA2 = store(STORE_A2_ID, "A2");
        Authentication authentication = authentication("manager@example.local",
                AuthorizationService.TENANT_SCOPE_AUTHORITY,
                "USER_VIEW_ASSIGNED_STORE_USERS");

        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(manager));
        when(userRepository.findByIdAndTenantId(cashier.getId(), TENANT_ID)).thenReturn(Optional.of(cashier));
        when(userRoleRepository.findByUser(manager)).thenReturn(List.of(new UserRole(manager, managerRole)));
        when(userRoleRepository.findByUser(cashier)).thenReturn(List.of(new UserRole(cashier, cashierRole)));
        when(userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, manager))
                .thenReturn(List.of(new UserStoreAssignment(TENANT_ID, manager, storeA1, AssignmentRole.MANAGER, manager.getId())));
        when(userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, cashier))
                .thenReturn(List.of(new UserStoreAssignment(TENANT_ID, cashier, storeA2, AssignmentRole.CASHIER, manager.getId())));

        assertThatThrownBy(() -> service.get(cashier.getId(), authentication))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("outside the permitted employee scope");
    }

    @Test
    void managerCannotPromoteCashierWithoutManagerCreatePermission() {
        User manager = tenantUser("manager@example.local", "Manager");
        User cashier = tenantUser("cashier@example.local", "Cashier");
        Role managerRole = new Role(RoleName.STORE_MANAGER, "Manager", true);
        Role cashierRole = new Role(RoleName.CASHIER, "Cashier", true);
        Store storeA1 = store(STORE_ID, "A1");
        Authentication authentication = authentication("manager@example.local",
                AuthorizationService.TENANT_SCOPE_AUTHORITY,
                "USER_UPDATE");

        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(manager));
        when(userRepository.findByIdAndTenantId(cashier.getId(), TENANT_ID)).thenReturn(Optional.of(cashier));
        when(userRoleRepository.findByUser(manager)).thenReturn(List.of(new UserRole(manager, managerRole)));
        when(userRoleRepository.findByUser(cashier)).thenReturn(List.of(new UserRole(cashier, cashierRole)));
        when(userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, manager))
                .thenReturn(List.of(new UserStoreAssignment(TENANT_ID, manager, storeA1, AssignmentRole.MANAGER, manager.getId())));
        when(userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(TENANT_ID, cashier))
                .thenReturn(List.of(new UserStoreAssignment(TENANT_ID, cashier, storeA1, AssignmentRole.CASHIER, manager.getId())));

        assertThatThrownBy(() -> service.replaceRolesAndAssignments(cashier.getId(), new UserRolesRequest(
                List.of(RoleName.STORE_MANAGER),
                List.of(STORE_ID),
                List.of(),
                cashier.getVersion()), authentication))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private User tenantUser(String email, String displayName) {
        User user = new User(email, displayName, "hash");
        user.assignTenant(TENANT_ID);
        return user;
    }

    private Store store(UUID id, String code) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(id);
        when(store.getTenantId()).thenReturn(TENANT_ID);
        when(store.getCode()).thenReturn(code);
        when(store.getName()).thenReturn(code);
        when(store.isActive()).thenReturn(true);
        return store;
    }

    private Authentication authentication(String name, String... authorities) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        when(authentication.isAuthenticated()).thenReturn(true);
        Collection<SimpleGrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        when(authentication.getAuthorities()).thenReturn((Collection) grantedAuthorities);
        return authentication;
    }
}
