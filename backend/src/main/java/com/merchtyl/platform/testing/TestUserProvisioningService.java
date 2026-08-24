package com.merchtyl.platform.testing;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.platform.admin.PlatformUserAccount;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.security.AssignmentRole;
import com.merchtyl.security.Role;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.RoleRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRole;
import com.merchtyl.security.UserRoleRepository;
import com.merchtyl.security.UserStoreAssignment;
import com.merchtyl.security.UserStoreAssignmentRepository;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
public class TestUserProvisioningService {
    private static final String TEST_EMAIL_DOMAIN = "@test.merchtyl.local";
    private static final Set<TestUserProvisioningRole> PLATFORM_ROLES =
            Set.of(TestUserProvisioningRole.PLATFORM_SUPER_ADMIN, TestUserProvisioningRole.PLATFORM_SUPPORT_ADMIN);

    private final TestUserProvisioningProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserStoreAssignmentRepository assignmentRepository;
    private final StoreRepository storeRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public TestUserProvisioningService(
            TestUserProvisioningProperties properties,
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserStoreAssignmentRepository assignmentRepository,
            StoreRepository storeRepository,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditService auditService) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.assignmentRepository = assignmentRepository;
        this.storeRepository = storeRepository;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    public boolean enabled() {
        return properties.enabled() && properties.key() != null && !properties.key().isBlank();
    }

    @Transactional
    public List<TestUserProvisioningDtos.ProvisionUserResponse> provision(TestUserProvisioningDtos.ProvisionUserRequest request) {
        List<TestUserProvisioningDtos.ProvisionUserRequest> expanded = expand(request);
        List<TestUserProvisioningDtos.ProvisionUserResponse> responses = new ArrayList<>();
        for (TestUserProvisioningDtos.ProvisionUserRequest entry : expanded) {
            responses.add(provisionOne(entry));
        }
        return responses;
    }

    @Transactional
    public List<TestUserProvisioningDtos.ProvisionUserResponse> provisionBatch(TestUserProvisioningDtos.BatchProvisionUsersRequest request) {
        if (request == null || request.users() == null || request.users().isEmpty()) {
            throw new BadRequestException("At least one user request is required");
        }
        List<TestUserProvisioningDtos.ProvisionUserResponse> responses = new ArrayList<>();
        for (TestUserProvisioningDtos.ProvisionUserRequest user : request.users()) {
            responses.addAll(provision(user));
        }
        audit(AuditAction.TEST_USER_BATCH_CREATED, "TEST_USER_BATCH", null, Map.of("count", responses.size()), null);
        return responses;
    }

    @Transactional
    public TestUserProvisioningDtos.CleanupResponse cleanup(TestUserProvisioningDtos.CleanupRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.testProvisionedOnly())) {
            throw new BadRequestException("Cleanup requires testProvisionedOnly=true");
        }
        List<User> users = tenantCleanupCandidates(request);
        int disabled = 0;
        for (User user : users) {
            if (!user.isTestProvisioned()) {
                continue;
            }
            if (request.createdBefore() != null && !user.getCreatedAt().isBefore(request.createdBefore())) {
                continue;
            }
            if (request.role() != null && !roles(user).contains(roleName(request.role()))) {
                continue;
            }
            if (user.isEnabled()) {
                user.disable();
                refreshTokenService.revokeActiveTokensForUser(user, Instant.now());
                disabled++;
                audit(AuditAction.TEST_USER_DISABLED, "USER", user.getId(),
                        Map.of("email", user.getEmail(), "tenantId", Objects.toString(user.getTenantId(), null)), null);
            }
        }
        disabled += cleanupPlatformUsers(request);
        audit(AuditAction.TEST_USER_CLEANUP_REQUESTED, "TEST_USER_CLEANUP", null,
                Map.of("disabledUsers", disabled), null);
        return new TestUserProvisioningDtos.CleanupResponse(disabled);
    }

    private TestUserProvisioningDtos.ProvisionUserResponse provisionOne(TestUserProvisioningDtos.ProvisionUserRequest request) {
        TestUserProvisioningRole role = request == null ? null : request.role();
        if (role == null) {
            throw new BadRequestException("role is required");
        }
        return PLATFORM_ROLES.contains(role) ? provisionPlatformUser(request) : provisionTenantUser(request);
    }

    private TestUserProvisioningDtos.ProvisionUserResponse provisionPlatformUser(TestUserProvisioningDtos.ProvisionUserRequest request) {
        if (hasText(request.tenantCode()) || !storeCodes(request.storeCodes()).isEmpty()) {
            throw new BadRequestException("Platform test users cannot receive tenant or store assignments");
        }
        String email = normalizeEmail(request.email());
        String displayName = displayName(request);
        String password = password(request);
        TestUserExistingStrategy onExisting = onExisting(request);
        TestUserProvisioningStatus status = status(request);
        boolean[] state = state(status);
        RoleName role = roleName(request.role());

        Optional<PlatformUserAccount> existing = platformUserRepository.findByEmail(email);
        if (existing.isPresent()) {
            PlatformUserAccount user = existing.get();
            if (onExisting == TestUserExistingStrategy.FAIL) {
                throw new ConflictException("A platform user with this email already exists");
            }
            if (onExisting == TestUserExistingStrategy.RETURN_EXISTING) {
                return platformResponse(user, false);
            }
            if (!user.testProvisioned()) {
                throw new ConflictException("Only helper-created platform users can be reset");
            }
            PlatformUserAccount reset = platformUserRepository.resetTestUser(
                    user.id(),
                    displayName,
                    passwordEncoder.encode(password),
                    role,
                    state[0],
                    state[1],
                    mustChangePassword(request),
                    user.version());
            audit(AuditAction.TEST_USER_RESET, "PLATFORM_USER", reset.id(), safeUserSnapshot(reset.email(), reset.role().name(), null, List.of()), null);
            return platformResponse(reset, false);
        }

        PlatformUserAccount created = platformUserRepository.createTestUser(
                email,
                displayName,
                passwordEncoder.encode(password),
                role,
                state[0],
                state[1],
                mustChangePassword(request),
                reference(request));
        audit(AuditAction.TEST_USER_CREATED, "PLATFORM_USER", created.id(), safeUserSnapshot(created.email(), created.role().name(), null, List.of()), null);
        return platformResponse(created, true);
    }

    private TestUserProvisioningDtos.ProvisionUserResponse provisionTenantUser(TestUserProvisioningDtos.ProvisionUserRequest request) {
        String tenantCode = normalizeRequiredCode(request.tenantCode(), "tenantCode");
        UUID tenantId = resolveTenant(tenantCode, Boolean.TRUE.equals(request.createTenantIfMissing()));
        String email = normalizeEmail(request.email());
        TestUserExistingStrategy onExisting = onExisting(request);
        RoleName role = roleName(request.role());
        TestUserProvisioningStatus status = status(request);
        List<Store> stores = resolveStores(tenantId, tenantCode, request);
        validateAssignmentRequirement(request.role(), status, stores);

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (onExisting == TestUserExistingStrategy.FAIL) {
                throw new ConflictException("A tenant user with this email already exists");
            }
            if (!tenantId.equals(user.getTenantId())) {
                throw new ConflictException("Existing user belongs to a different tenant");
            }
            if (onExisting == TestUserExistingStrategy.RETURN_EXISTING) {
                return tenantResponse(user, tenantCode, request.role(), false);
            }
            if (!user.isTestProvisioned()) {
                throw new ConflictException("Only helper-created tenant users can be reset");
            }
            resetTenantUser(user, request, role, status, stores);
            audit(AuditAction.TEST_USER_RESET, "USER", user.getId(), safeUserSnapshot(user.getEmail(), role.name(), tenantCode, storesToCodes(stores)), null);
            return tenantResponse(user, tenantCode, request.role(), false);
        }

        User user = new User(email, displayName(request), passwordEncoder.encode(password(request)));
        user.assignTenant(tenantId);
        applyStatus(user, status);
        user.setPasswordChangeRequired(mustChangePassword(request));
        user.markTestProvisioned(reference(request), Instant.now());
        User saved = userRepository.save(user);
        replaceRole(saved, role);
        replaceAssignments(saved, tenantId, stores, assignmentRole(request.role()));
        audit(AuditAction.TEST_USER_CREATED, "USER", saved.getId(), safeUserSnapshot(saved.getEmail(), role.name(), tenantCode, storesToCodes(stores)), null);
        return tenantResponse(saved, tenantCode, request.role(), true);
    }

    private void resetTenantUser(
            User user,
            TestUserProvisioningDtos.ProvisionUserRequest request,
            RoleName role,
            TestUserProvisioningStatus status,
            List<Store> stores) {
        user.updateProfile(normalizeEmail(request.email()), displayName(request), false);
        user.changePasswordHash(passwordEncoder.encode(password(request)));
        user.setPasswordChangeRequired(mustChangePassword(request));
        applyStatus(user, status);
        user.markTestProvisioned(reference(request), Instant.now());
        replaceRole(user, role);
        replaceAssignments(user, user.getTenantId(), stores, assignmentRole(request.role()));
        refreshTokenService.revokeActiveTokensForUser(user, Instant.now());
        userRepository.save(user);
    }

    private void replaceRole(User user, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleName.name()));
        userRoleRepository.deleteByUser(user);
        userRoleRepository.save(new UserRole(user, role));
    }

    private void replaceAssignments(User user, UUID tenantId, List<Store> stores, AssignmentRole assignmentRole) {
        assignmentRepository.findByTenantIdAndUser_Id(tenantId, user.getId())
                .forEach(assignment -> {
                    if (assignment.isActive()) {
                        assignment.revoke(user.getId(), "Test provisioning reset");
                    }
                });
        if (assignmentRole == null) {
            return;
        }
        for (Store store : stores) {
            if (!tenantId.equals(store.getTenantId())) {
                audit(AuditAction.CROSS_TENANT_TEST_ASSIGNMENT_REJECTED, "STORE", store.getId(),
                        Map.of("userId", user.getId(), "storeCode", store.getCode()), null);
                throw new ForbiddenOperationException("Store does not belong to the requested tenant");
            }
            UserStoreAssignment assignment = assignmentRepository
                    .findByTenantIdAndUser_IdAndStore_IdAndAssignmentRole(tenantId, user.getId(), store.getId(), assignmentRole)
                    .orElseGet(() -> new UserStoreAssignment(tenantId, user, store, assignmentRole, user.getId()));
            if (!assignment.isActive()) {
                assignment.reactivate(assignmentRole, user.getId());
            }
            assignmentRepository.save(assignment);
        }
    }

    private UUID resolveTenant(String tenantCode, boolean createIfMissing) {
        Optional<UUID> existing = tenantId(tenantCode);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!createIfMissing) {
            throw new NotFoundException("Tenant not found");
        }
        requireTestCode(tenantCode, "tenantCode");
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into tenants (
                    id, tenant_code, legal_name, display_name, status, country_code,
                    default_currency_code, primary_timezone, activated_at
                )
                values (?, ?, ?, ?, 'ACTIVE', 'CA', 'CAD', 'America/Moncton', now())
                """, tenantId, tenantCode, tenantCode + " Legal Test Merchant", tenantCode + " Test Merchant");
        jdbcTemplate.update("""
                insert into merchant_profiles (
                    id, tenant_id, legal_business_name, operating_name, contact_name,
                    contact_email, country_code, administrative_division_code, industry_type, notes
                )
                values (?, ?, ?, ?, 'Test Provisioning', ?, 'CA', 'NB', 'TEST', 'Created by development test provisioning helper')
                on conflict (tenant_id) do nothing
                """, UUID.randomUUID(), tenantId, tenantCode + " Legal Test Merchant", tenantCode + " Test Merchant",
                "contact." + tenantCode.toLowerCase(Locale.ROOT) + TEST_EMAIL_DOMAIN);
        UUID onboardingId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into tenant_onboardings (id, tenant_id, current_stage, completed_at)
                values (?, ?, 'COMPLETED', now())
                on conflict (tenant_id) do nothing
                """, onboardingId, tenantId);
        jdbcTemplate.update("""
                insert into tenant_subscriptions (id, tenant_id, plan_code, status, starts_at, trial_ends_at, maximum_stores, maximum_users, features)
                values (?, ?, 'TEST', 'TRIAL', now(), now() + interval '14 days', 25, 250, '{}'::jsonb)
                on conflict (tenant_id) do nothing
                """, UUID.randomUUID(), tenantId);
        audit(AuditAction.TEST_SCENARIO_CREATED, "TENANT", tenantId, Map.of("tenantCode", tenantCode), null);
        return tenantId;
    }

    private Optional<UUID> tenantId(String tenantCode) {
        try {
            UUID id = jdbcTemplate.queryForObject("""
                    select id from tenants where upper(tenant_code) = upper(?)
                    """, UUID.class, tenantCode);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private List<Store> resolveStores(UUID tenantId, String tenantCode, TestUserProvisioningDtos.ProvisionUserRequest request) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(storeCodes(request.storeCodes()));
        if (codes.isEmpty()) {
            return List.of();
        }
        List<Store> stores = new ArrayList<>();
        for (String code : codes) {
            Store store = storeRepository.findByCodeIgnoreCase(code)
                    .orElseGet(() -> createStore(tenantId, tenantCode, code, Boolean.TRUE.equals(request.createStoresIfMissing())));
            if (!tenantId.equals(store.getTenantId())) {
                audit(AuditAction.CROSS_TENANT_TEST_ASSIGNMENT_REJECTED, "STORE", store.getId(),
                        Map.of("tenantCode", tenantCode, "storeCode", code), null);
                throw new ForbiddenOperationException("Store does not belong to the requested tenant");
            }
            stores.add(store);
        }
        return stores;
    }

    private Store createStore(UUID tenantId, String tenantCode, String storeCode, boolean createIfMissing) {
        if (!createIfMissing) {
            throw new NotFoundException("Store not found: " + storeCode);
        }
        requireTestCode(tenantCode, "tenantCode");
        if (!storeCode.startsWith("TEST-") && !storeCode.startsWith("STORE-")) {
            throw new BadRequestException("Auto-created test store codes must start with TEST- or STORE-");
        }
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into stores (
                    id, code, name, legal_name, country_code, administrative_area_code,
                    address, currency_code, locale, timezone, timezone_name,
                    prices_include_tax, negative_stock_allowed, active, tenant_id
                )
                values (?, ?, ?, ?, 'CA', 'NB', ?, 'CAD', 'en-CA', 'America/Moncton', 'America/Moncton',
                        false, false, true, ?)
                """, id, storeCode, storeCode + " Test Store", storeCode + " Legal Test Store",
                "1 Test Street, Moncton, NB", tenantId);
        audit(AuditAction.TEST_SCENARIO_CREATED, "STORE", id, Map.of("tenantCode", tenantCode, "storeCode", storeCode), null);
        return storeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Created store could not be loaded"));
    }

    private List<TestUserProvisioningDtos.ProvisionUserRequest> expand(TestUserProvisioningDtos.ProvisionUserRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (quantity < 1 || quantity > 100) {
            throw new BadRequestException("quantity must be between 1 and 100");
        }
        if (!Boolean.TRUE.equals(request.generateRandomData()) && quantity == 1) {
            return List.of(request);
        }
        if (!Boolean.TRUE.equals(request.generateRandomData())) {
            throw new BadRequestException("quantity greater than 1 requires generateRandomData=true");
        }
        String prefix = hasText(request.emailPrefix()) ? clean(request.emailPrefix()).toLowerCase(Locale.ROOT) : request.role().name().toLowerCase(Locale.ROOT);
        Random random = new Random(request.randomSeed() == null ? 0L : request.randomSeed());
        List<TestUserProvisioningDtos.ProvisionUserRequest> requests = new ArrayList<>();
        for (int i = 1; i <= quantity; i++) {
            String suffix = String.format(Locale.ROOT, "%03d", i);
            String firstName = title(prefix);
            String lastName = "Test" + (100 + random.nextInt(900)) + suffix;
            requests.add(new TestUserProvisioningDtos.ProvisionUserRequest(
                    request.tenantCode(),
                    request.role(),
                    firstName,
                    lastName,
                    prefix + "." + suffix + TEST_EMAIL_DOMAIN,
                    request.password(),
                    request.status(),
                    request.storeCodes(),
                    request.mustChangePassword(),
                    request.createTenantIfMissing(),
                    request.createStoresIfMissing(),
                    null,
                    false,
                    request.emailPrefix(),
                    request.defaultPassword(),
                    request.randomSeed(),
                    request.onExisting()));
        }
        return requests;
    }

    private List<User> tenantCleanupCandidates(TestUserProvisioningDtos.CleanupRequest request) {
        if (request.role() != null && PLATFORM_ROLES.contains(request.role())) {
            return List.of();
        }
        if (hasText(request.tenantCode())) {
            UUID tenantId = tenantId(normalizeRequiredCode(request.tenantCode(), "tenantCode"))
                    .orElseThrow(() -> new NotFoundException("Tenant not found"));
            return userRepository.findByTenantIdAndTestProvisionedTrue(tenantId);
        }
        if (hasText(request.emailPattern())) {
            return userRepository.findByTestProvisionedTrueAndEmailContainingIgnoreCase(clean(request.emailPattern()));
        }
        return userRepository.findByTestProvisionedTrue();
    }

    private int cleanupPlatformUsers(TestUserProvisioningDtos.CleanupRequest request) {
        if (hasText(request.tenantCode())) {
            return 0;
        }
        if (request.role() != null && !PLATFORM_ROLES.contains(request.role())) {
            return 0;
        }
        List<PlatformUserAccount> users = hasText(request.emailPattern())
                ? platformUserRepository.findTestProvisionedByEmailContaining(clean(request.emailPattern()))
                : platformUserRepository.findTestProvisioned();
        int disabled = 0;
        for (PlatformUserAccount user : users) {
            if (!user.testProvisioned()) {
                continue;
            }
            if (request.createdBefore() != null && !user.createdAt().isBefore(request.createdBefore())) {
                continue;
            }
            if (request.role() != null && user.role() != roleName(request.role())) {
                continue;
            }
            if (user.enabled()) {
                PlatformUserAccount disabledUser = platformUserRepository.disableTestUser(user.id(), user.version());
                disabled++;
                audit(AuditAction.TEST_USER_DISABLED, "PLATFORM_USER", disabledUser.id(),
                        Map.of("email", disabledUser.email(), "role", disabledUser.role().name()), null);
            }
        }
        return disabled;
    }

    private TestUserProvisioningDtos.ProvisionUserResponse tenantResponse(
            User user,
            String tenantCode,
            TestUserProvisioningRole role,
            boolean created) {
        List<String> assignedStores = assignmentRepository.findByTenantIdAndUserAndActiveTrue(user.getTenantId(), user).stream()
                .map(assignment -> assignment.getStore().getCode())
                .sorted()
                .toList();
        return new TestUserProvisioningDtos.ProvisionUserResponse(
                user.getId(),
                "TENANT",
                tenantCode,
                role,
                user.getEmail(),
                status(user),
                assignedStores,
                created,
                true);
    }

    private TestUserProvisioningDtos.ProvisionUserResponse platformResponse(PlatformUserAccount user, boolean created) {
        return new TestUserProvisioningDtos.ProvisionUserResponse(
                user.id(),
                "PLATFORM",
                null,
                TestUserProvisioningRole.valueOf(user.role().name()),
                user.email(),
                status(user.enabled(), user.locked()),
                List.of(),
                created,
                true);
    }

    private List<String> storeCodes(List<String> storeCodes) {
        if (storeCodes == null) {
            return List.of();
        }
        return storeCodes.stream()
                .filter(Objects::nonNull)
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> storesToCodes(List<Store> stores) {
        return stores.stream().map(Store::getCode).sorted().toList();
    }

    private void validateAssignmentRequirement(
            TestUserProvisioningRole role,
            TestUserProvisioningStatus status,
            List<Store> stores) {
        if ((role == TestUserProvisioningRole.STORE_MANAGER || role == TestUserProvisioningRole.CASHIER)
                && (status == TestUserProvisioningStatus.ACTIVE || status == TestUserProvisioningStatus.LOCKED)
                && stores.isEmpty()) {
            throw new BadRequestException("Active managers and cashiers require at least one store assignment");
        }
    }

    private AssignmentRole assignmentRole(TestUserProvisioningRole role) {
        if (role == TestUserProvisioningRole.STORE_MANAGER) {
            return AssignmentRole.MANAGER;
        }
        if (role == TestUserProvisioningRole.CASHIER) {
            return AssignmentRole.CASHIER;
        }
        return null;
    }

    private RoleName roleName(TestUserProvisioningRole role) {
        return RoleName.valueOf(role.name());
    }

    private Set<RoleName> roles(User user) {
        return userRoleRepository.findByUser(user).stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(java.util.stream.Collectors.toSet());
    }

    private String displayName(TestUserProvisioningDtos.ProvisionUserRequest request) {
        String first = cleanRequired(request.firstName(), "firstName");
        String last = cleanRequired(request.lastName(), "lastName");
        return (first + " " + last).trim();
    }

    private String password(TestUserProvisioningDtos.ProvisionUserRequest request) {
        String password = firstText(request.password(), request.defaultPassword(), properties.defaultUserPassword());
        if (!hasText(password)) {
            throw new BadRequestException("password is required when no test default password is configured");
        }
        if (password.length() < 8 || password.length() > 128) {
            throw new BadRequestException("password must be between 8 and 128 characters");
        }
        return password;
    }

    private boolean mustChangePassword(TestUserProvisioningDtos.ProvisionUserRequest request) {
        return Boolean.TRUE.equals(request.mustChangePassword());
    }

    private TestUserExistingStrategy onExisting(TestUserProvisioningDtos.ProvisionUserRequest request) {
        return request.onExisting() == null ? TestUserExistingStrategy.FAIL : request.onExisting();
    }

    private TestUserProvisioningStatus status(TestUserProvisioningDtos.ProvisionUserRequest request) {
        return request.status() == null ? TestUserProvisioningStatus.ACTIVE : request.status();
    }

    private TestUserProvisioningStatus status(User user) {
        return status(user.isEnabled(), user.isLocked());
    }

    private TestUserProvisioningStatus status(boolean enabled, boolean locked) {
        if (locked) {
            return TestUserProvisioningStatus.LOCKED;
        }
        return enabled ? TestUserProvisioningStatus.ACTIVE : TestUserProvisioningStatus.DISABLED;
    }

    private void applyStatus(User user, TestUserProvisioningStatus status) {
        boolean[] state = state(status);
        if (state[0]) {
            user.enable();
        } else {
            user.disable();
        }
        if (state[1]) {
            user.lock();
        } else {
            user.unlock();
        }
    }

    private boolean[] state(TestUserProvisioningStatus status) {
        return switch (status) {
            case ACTIVE -> new boolean[]{true, false};
            case DISABLED, INVITED -> new boolean[]{false, false};
            case LOCKED -> new boolean[]{true, true};
        };
    }

    private String reference(TestUserProvisioningDtos.ProvisionUserRequest request) {
        String scope = PLATFORM_ROLES.contains(request.role()) ? "platform" : normalizeRequiredCode(request.tenantCode(), "tenantCode");
        return "test-provisioning:" + scope + ":" + normalizeEmail(request.email());
    }

    private Map<String, Object> safeUserSnapshot(String email, String role, String tenantCode, List<String> storeCodes) {
        return Map.of(
                "email", email,
                "role", role,
                "tenantCode", tenantCode == null ? "" : tenantCode,
                "storeCodes", storeCodes);
    }

    private void audit(AuditAction action, String entityType, UUID entityId, Object after, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                null,
                action,
                entityType,
                entityId,
                null,
                null,
                null,
                after,
                reason));
    }

    private String normalizeEmail(String email) {
        String normalized = cleanRequired(email, "email").toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new BadRequestException("email must be valid");
        }
        return normalized;
    }

    private String normalizeRequiredCode(String value, String field) {
        return cleanRequired(value, field).toUpperCase(Locale.ROOT);
    }

    private void requireTestCode(String value, String field) {
        if (!normalizeRequiredCode(value, field).startsWith("TEST-")) {
            throw new BadRequestException(field + " must start with TEST- when auto-created");
        }
    }

    private String cleanRequired(String value, String field) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String title(String value) {
        if (!hasText(value)) {
            return "Test";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
