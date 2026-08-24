package com.merchtyl.platform.testing;

import com.merchtyl.audit.AuditService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.platform.admin.PlatformUserAccount;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.security.AssignmentRole;
import com.merchtyl.security.Role;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.RoleRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRoleRepository;
import com.merchtyl.security.UserStoreAssignmentRepository;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestUserProvisioningServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final UserStoreAssignmentRepository assignmentRepository = mock(UserStoreAssignmentRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TestUserProvisioningService service = new TestUserProvisioningService(
            new TestUserProvisioningProperties(true, "test-key", ""),
            jdbcTemplate,
            userRepository,
            roleRepository,
            userRoleRepository,
            assignmentRepository,
            storeRepository,
            platformUserRepository,
            new BCryptPasswordEncoder(),
            refreshTokenService,
            auditService);

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(roleRepository.findByName(RoleName.STORE_MANAGER))
                .thenReturn(Optional.of(new Role(RoleName.STORE_MANAGER, "Store manager", true)));
        when(roleRepository.findByName(RoleName.CASHIER))
                .thenReturn(Optional.of(new Role(RoleName.CASHIER, "Cashier", true)));
        when(roleRepository.findByName(RoleName.TENANT_OWNER))
                .thenReturn(Optional.of(new Role(RoleName.TENANT_OWNER, "Owner", true)));
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), any()))
                .thenReturn(tenantId);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.findByTenantIdAndUserAndActiveTrue(eq(tenantId), any(User.class)))
                .thenReturn(List.of());
    }

    @Test
    void createsTenantManagerWithMultipleStoreAssignments() {
        Store storeA1 = store("STORE-A1", tenantId);
        Store storeA2 = store("STORE-A2", tenantId);
        when(storeRepository.findByCodeIgnoreCase("STORE-A1")).thenReturn(Optional.of(storeA1));
        when(storeRepository.findByCodeIgnoreCase("STORE-A2")).thenReturn(Optional.of(storeA2));
        when(userRepository.findByEmailIgnoreCase("manager.a@test.merchtyl.local")).thenReturn(Optional.empty());

        var responses = service.provision(new TestUserProvisioningDtos.ProvisionUserRequest(
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.STORE_MANAGER,
                "Manager",
                "A",
                "manager.a@test.merchtyl.local",
                "Test1234!",
                TestUserProvisioningStatus.ACTIVE,
                List.of("STORE-A1", "STORE-A2"),
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                TestUserExistingStrategy.FAIL));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).accountScope()).isEqualTo("TENANT");
        ArgumentCaptor<com.merchtyl.security.UserStoreAssignment> captor =
                ArgumentCaptor.forClass(com.merchtyl.security.UserStoreAssignment.class);
        verify(assignmentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(com.merchtyl.security.UserStoreAssignment::getAssignmentRole)
                .containsOnly(AssignmentRole.MANAGER);
    }

    @Test
    void rejectsCrossTenantStoreAssignment() {
        Store foreignStore = store("STORE-B1", UUID.randomUUID());
        when(storeRepository.findByCodeIgnoreCase("STORE-B1")).thenReturn(Optional.of(foreignStore));

        assertThatThrownBy(() -> service.provision(new TestUserProvisioningDtos.ProvisionUserRequest(
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.CASHIER,
                "Cashier",
                "A",
                "cashier.a@test.merchtyl.local",
                "Test1234!",
                TestUserProvisioningStatus.ACTIVE,
                List.of("STORE-B1"),
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                TestUserExistingStrategy.FAIL)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void platformUserCannotReceiveTenantAssignments() {
        assertThatThrownBy(() -> service.provision(new TestUserProvisioningDtos.ProvisionUserRequest(
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.PLATFORM_SUPER_ADMIN,
                "Platform",
                "Admin",
                "platform.admin@test.merchtyl.local",
                "Test1234!",
                TestUserProvisioningStatus.ACTIVE,
                List.of("STORE-A1"),
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                TestUserExistingStrategy.FAIL)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetExistingNormalTenantUserIsRejected() {
        User normalUser = new User("cashier.a@test.merchtyl.local", "Cashier A", "hash");
        normalUser.assignTenant(tenantId);
        when(userRepository.findByEmailIgnoreCase("cashier.a@test.merchtyl.local")).thenReturn(Optional.of(normalUser));

        assertThatThrownBy(() -> service.provision(new TestUserProvisioningDtos.ProvisionUserRequest(
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.CASHIER,
                "Cashier",
                "A",
                "cashier.a@test.merchtyl.local",
                "Test1234!",
                TestUserProvisioningStatus.DISABLED,
                List.of(),
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                TestUserExistingStrategy.RESET_TEST_USER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cleanupSkipsNormalUsersEvenWhenReturnedByRepository() {
        User normalUser = new User("cashier.a@test.merchtyl.local", "Cashier A", "hash");
        normalUser.assignTenant(tenantId);
        when(userRepository.findByTestProvisionedTrue()).thenReturn(List.of(normalUser));
        when(platformUserRepository.findTestProvisioned()).thenReturn(List.of());

        var response = service.cleanup(new TestUserProvisioningDtos.CleanupRequest(
                null,
                null,
                null,
                null,
                true));

        assertThat(response.disabledUsers()).isZero();
        assertThat(normalUser.isEnabled()).isTrue();
        verify(refreshTokenService, never()).revokeActiveTokensForUser(any(), any());
    }

    @Test
    void cleanupDisablesHelperCreatedPlatformUsers() {
        PlatformUserAccount account = new PlatformUserAccount(
                UUID.randomUUID(),
                "platform.admin@test.merchtyl.local",
                "Platform Admin",
                "hash",
                RoleName.PLATFORM_SUPER_ADMIN,
                true,
                false,
                false,
                true,
                "test-provisioning:platform:platform.admin@test.merchtyl.local",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                0);
        PlatformUserAccount disabled = new PlatformUserAccount(
                account.id(),
                account.email(),
                account.displayName(),
                account.passwordHash(),
                account.role(),
                false,
                account.locked(),
                account.passwordChangeRequired(),
                account.testProvisioned(),
                account.testProvisioningReference(),
                account.testProvisionedAt(),
                account.createdAt(),
                Instant.now(),
                1);
        when(userRepository.findByTestProvisionedTrue()).thenReturn(List.of());
        when(platformUserRepository.findTestProvisioned()).thenReturn(List.of(account));
        when(platformUserRepository.disableTestUser(account.id(), account.version())).thenReturn(disabled);

        var response = service.cleanup(new TestUserProvisioningDtos.CleanupRequest(
                null,
                null,
                TestUserProvisioningRole.PLATFORM_SUPER_ADMIN,
                null,
                true));

        assertThat(response.disabledUsers()).isEqualTo(1);
        verify(platformUserRepository).disableTestUser(account.id(), account.version());
    }

    @Test
    void randomDataGenerationIsDeterministic() {
        Store storeA1 = store("STORE-A1", tenantId);
        when(storeRepository.findByCodeIgnoreCase("STORE-A1")).thenReturn(Optional.of(storeA1));
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        var responses = service.provision(new TestUserProvisioningDtos.ProvisionUserRequest(
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.CASHIER,
                null,
                null,
                null,
                "Test1234!",
                TestUserProvisioningStatus.ACTIVE,
                List.of("STORE-A1"),
                false,
                false,
                false,
                3,
                true,
                "cashier",
                null,
                42L,
                TestUserExistingStrategy.FAIL));

        assertThat(responses).extracting(TestUserProvisioningDtos.ProvisionUserResponse::email)
                .containsExactly(
                        "cashier.001@test.merchtyl.local",
                        "cashier.002@test.merchtyl.local",
                        "cashier.003@test.merchtyl.local");
    }

    @Test
    void createsPlatformSuperAdminWithPlatformScope() {
        PlatformUserAccount account = new PlatformUserAccount(
                UUID.randomUUID(),
                "platform.admin@test.merchtyl.local",
                "Platform Admin",
                "hash",
                RoleName.PLATFORM_SUPER_ADMIN,
                true,
                false,
                false,
                true,
                "test-provisioning:platform:platform.admin@test.merchtyl.local",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                0);
        when(platformUserRepository.findByEmail("platform.admin@test.merchtyl.local")).thenReturn(Optional.empty());
        when(platformUserRepository.createTestUser(
                eq("platform.admin@test.merchtyl.local"),
                eq("Platform Admin"),
                anyString(),
                eq(RoleName.PLATFORM_SUPER_ADMIN),
                eq(true),
                eq(false),
                eq(false),
                anyString()))
                .thenReturn(account);

        var response = service.provision(new TestUserProvisioningDtos.ProvisionUserRequest(
                null,
                TestUserProvisioningRole.PLATFORM_SUPER_ADMIN,
                "Platform",
                "Admin",
                "platform.admin@test.merchtyl.local",
                "Test1234!",
                TestUserProvisioningStatus.ACTIVE,
                List.of(),
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                TestUserExistingStrategy.FAIL)).get(0);

        assertThat(response.accountScope()).isEqualTo("PLATFORM");
        assertThat(response.assignedStores()).isEmpty();
    }

    private Store store(String code, UUID tenantId) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(UUID.randomUUID());
        when(store.getCode()).thenReturn(code);
        when(store.getTenantId()).thenReturn(tenantId);
        return store;
    }
}
