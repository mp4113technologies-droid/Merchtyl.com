package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.auth.PasswordPolicyService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.DatabaseConstraintErrorMapper;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.store.StoreCapability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAdministrationService {
    private static final Logger log = LoggerFactory.getLogger(UserAdministrationService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<RoleName> EMPLOYEE_ROLES = Set.of(RoleName.STORE_MANAGER, RoleName.CASHIER, RoleName.KITCHEN);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserStoreAssignmentRepository userStoreAssignmentRepository;
    private final UserRegisterAssignmentRepository userRegisterAssignmentRepository;
    private final StoreRepository storeRepository;
    private final RegisterRepository registerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditService auditService;
    private final StoreAccessService storeAccessService;

    @Autowired
    public UserAdministrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserStoreAssignmentRepository userStoreAssignmentRepository,
            UserRegisterAssignmentRepository userRegisterAssignmentRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            AuditService auditService,
            StoreAccessService storeAccessService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userStoreAssignmentRepository = userStoreAssignmentRepository;
        this.userRegisterAssignmentRepository = userRegisterAssignmentRepository;
        this.storeRepository = storeRepository;
        this.registerRepository = registerRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.auditService = auditService;
        this.storeAccessService = storeAccessService;
    }

    UserAdministrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserStoreAssignmentRepository userStoreAssignmentRepository,
            UserRegisterAssignmentRepository userRegisterAssignmentRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this(userRepository, roleRepository, userRoleRepository, userStoreAssignmentRepository,
                userRegisterAssignmentRepository, storeRepository, registerRepository, passwordEncoder,
                new PasswordPolicyService(), auditService, null);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request, Authentication authentication) {
        passwordPolicyService.validate(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw duplicateEmail();
        }
        User actor = actor(authentication);
        RoleName roleName = employeeRole(request.roles());
        log.info("security_event event=MERCHANT_USER_CREATE_STARTED tenant_id={} actor_user_id={} actor_role={} requested_role={}",
                actor.getTenantId(), actor.getId(), actorCreatorRole(actor), roleName);
        requireCanCreate(actor, roleName, authentication);
        Set<UUID> storeIds = ids(request.storeIds());
        boolean enabled = !Boolean.FALSE.equals(request.enabled());
        if (enabled && storeIds.isEmpty()) {
            throw new BadRequestException("At least one store assignment is required for an active user");
        }
        List<Store> stores = storesForAssignment(actor, storeIds);
        validateRoleStoreCapabilities(roleName, stores);
        validateRegisterAssignments(actor, ids(request.registerIds()), storeIds);

        User user = new User(email, cleanRequired(request.displayName(), "displayName"), passwordEncoder.encode(request.password()));
        user.assignTenant(actor.getTenantId());
        user.markCreatedBy(actor.getId(), actorCreatorRole(actor));
        if (!enabled) {
            user.disable();
        }
        if (Boolean.TRUE.equals(request.locked())) {
            user.lock();
        }

        User saved = save(user);
        log.debug("security_event event=SECURITY_USER_CREATED tenant_id={} actor_user_id={} user_id={}",
                actor.getTenantId(), actor.getId(), saved.getId());
        replaceRoles(saved, List.of(roleName));
        replaceRegisterAssignments(actor, saved, ids(request.registerIds()), storeIds);
        replaceStoreAssignments(actor, saved, stores, roleName, null);
        log.debug("security_event event=USER_STORE_ASSIGNMENTS_CREATED tenant_id={} user_id={} assignment_count={}",
                actor.getTenantId(), saved.getId(), stores.size());
        UserResponse response = response(saved);
        audit(authentication, createAction(roleName), saved.getId(), null, response, "USER", null, null);
        log.info("security_event event=TENANT_USER_CREATED tenant_id={} actor_user_id={} actor_role={} created_user_id={} created_user_role={}",
                actor.getTenantId(), actor.getId(), actorCreatorRole(actor), saved.getId(), roleName);
        log.info("security_event event=MERCHANT_USER_CREATE_COMPLETED tenant_id={} actor_user_id={} user_id={} role={}",
                actor.getTenantId(), actor.getId(), saved.getId(), roleName);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UserSearchRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isCashier(actorRoles) && !StoreAccessService.isOwner(actorRoles) && !StoreAccessService.isManager(actorRoles)) {
            throw new ForbiddenOperationException("Cashiers cannot access the user directory");
        }
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Direction.ASC, "displayName").and(Sort.by(Sort.Direction.ASC, "id")));
        var spec = scopedSpecification(request, actor, actorRoles, authentication);
        RoleName authenticatedRole = actorCreatorRole(actor);
        log.debug("security_event event=MERCHANT_USER_LIST_REQUESTED authenticated_user_id={} authenticated_role={}",
                actor.getId(), authenticatedRole);
        log.debug("security_event event=MERCHANT_USER_LIST_TENANT_RESOLVED authenticated_user_id={} authenticated_role={} tenant_id={}",
                actor.getId(), authenticatedRole, actor.getTenantId());
        var page = userRepository.findAll(spec, pageable);
        log.debug("security_event event=MERCHANT_USER_LIST_REPOSITORY_RESULT authenticated_user_id={} authenticated_role={} tenant_id={} repository_result_count={}",
                actor.getId(), authenticatedRole, actor.getTenantId(), page.getNumberOfElements());
        List<UserResponse> responses = responses(page.getContent(), actor.getTenantId());
        log.debug("security_event event=MERCHANT_USER_LIST_RESPONSE_MAPPED authenticated_user_id={} authenticated_role={} tenant_id={} repository_result_count={} response_count={}",
                actor.getId(), authenticatedRole, actor.getTenantId(), page.getNumberOfElements(), responses.size());
        return new PageResponse<>(
                responses,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UserSearchRequest request) {
        return search(request, null);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id, Authentication authentication) {
        User actor = actor(authentication);
        User target = permittedTarget(actor, id, true, authentication);
        return response(target);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        requireCanModify(actor, user, primaryEmployeeRole(user), authentication);
        requireCurrentVersion(user, request.version());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw duplicateEmail();
        }
        RoleName roleName = primaryEmployeeRole(user);
        Set<UUID> storeIds = ids(request.storeIds());
        if (user.isEnabled() && storeIds.isEmpty()) {
            throw new BadRequestException("At least one store assignment is required for an active user");
        }
        List<Store> stores = storesForAssignment(actor, storeIds);
        validateRoleStoreCapabilities(roleName, stores);
        validateRegisterAssignments(actor, ids(request.registerIds()), storeIds);

        UserResponse before = response(user);
        user.updateProfile(email, cleanRequired(request.displayName(), "displayName"), request.locked());
        user.markUpdatedBy(actor.getId());
        replaceRegisterAssignments(actor, user, ids(request.registerIds()), storeIds);
        replaceStoreAssignments(actor, user, stores, roleName, null);
        UserResponse after = response(save(user));
        audit(authentication, AuditAction.USER_UPDATED, id, before, after, "USER", null, null);
        return after;
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatusRequest request, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        requireCanModify(actor, user, primaryEmployeeRole(user), authentication);
        requireCurrentVersion(user, request.version());
        if (request.enabled() && activeStoreAssignments(user).isEmpty()) {
            throw new BadRequestException("At least one active store assignment is required before reactivation");
        }

        UserResponse before = response(user);
        if (request.enabled()) {
            user.enable();
        } else {
            user.disable();
        }
        user.markUpdatedBy(actor.getId());
        UserResponse after = response(save(user));
        audit(authentication, request.enabled() ? AuditAction.USER_REACTIVATED : AuditAction.USER_STATUS_CHANGED,
                id, before, after, "USER", null, null);
        return after;
    }

    @Transactional
    public UserResponse resetPassword(UUID id, UserPasswordResetRequest request, Authentication authentication) {
        passwordPolicyService.validate(request.newPassword());
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        requireCanModify(actor, user, primaryEmployeeRole(user), authentication);
        requireCurrentVersion(user, request.version());

        UserResponse before = response(user);
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        user.markUpdatedBy(actor.getId());
        UserResponse after = response(save(user));
        audit(authentication, AuditAction.USER_PASSWORD_RESET, id, before, after, "USER", null, null);
        return after;
    }

    @Transactional
    public UserResponse replaceRolesAndAssignments(UUID id, UserRolesRequest request, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        requireCurrentVersion(user, request.version());
        RoleName roleName = employeeRole(request.roles());
        requireCanModify(actor, user, roleName, authentication);
        Set<UUID> storeIds = ids(request.storeIds());
        if (user.isEnabled() && storeIds.isEmpty()) {
            throw new BadRequestException("At least one store assignment is required for an active user");
        }
        List<Store> stores = storesForAssignment(actor, storeIds);
        validateRoleStoreCapabilities(roleName, stores);
        validateRegisterAssignments(actor, ids(request.registerIds()), storeIds);

        UserResponse before = response(user);
        replaceRoles(user, List.of(roleName));
        user.markUpdatedBy(actor.getId());
        replaceRegisterAssignments(actor, user, ids(request.registerIds()), storeIds);
        replaceStoreAssignments(actor, user, stores, roleName, null);
        UserResponse after = response(save(user));
        audit(authentication, AuditAction.ROLE_CHANGED, id, before, after, "USER", null, null);
        return after;
    }

    @Transactional(readOnly = true)
    public List<UserStoreAssignmentResponse> storeAssignments(UUID id, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        return userStoreAssignmentRepository.findByTenantIdAndUser_Id(actor.getTenantId(), user.getId()).stream()
                .map(UserStoreAssignmentResponse::from)
                .toList();
    }

    @Transactional
    public List<UserStoreAssignmentResponse> addStoreAssignments(UUID id, UserStoreAssignmentRequest request, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        RoleName roleName = employeeRoleForAssignment(request.assignmentRole());
        requireCanModify(actor, user, roleName, authentication);
        requireRoleCompatible(user, roleName);
        List<Store> stores = storesForAssignment(actor, ids(request.storeIds()));
        validateRoleStoreCapabilities(roleName, stores);
        addAssignments(actor, user, stores, request.assignmentRole());
        return storeAssignments(id, authentication);
    }

    @Transactional
    public List<UserStoreAssignmentResponse> replaceStoreAssignments(UUID id, UserStoreAssignmentRequest request, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        RoleName roleName = employeeRoleForAssignment(request.assignmentRole());
        requireCanModify(actor, user, roleName, authentication);
        requireRoleCompatible(user, roleName);
        List<Store> stores = storesForAssignment(actor, ids(request.storeIds()));
        validateRoleStoreCapabilities(roleName, stores);
        replaceStoreAssignments(actor, user, stores, roleName, request.removalReason());
        return storeAssignments(id, authentication);
    }

    @Transactional
    public void removeStoreAssignment(UUID id, UUID storeId, Authentication authentication) {
        User actor = actor(authentication);
        User user = permittedTarget(actor, id, true, authentication);
        requireCanModify(actor, user, primaryEmployeeRole(user), authentication);
        Store store = storesForAssignment(actor, Set.of(storeId)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Store not found"));
        List<UserStoreAssignment> assignments = userStoreAssignmentRepository.findByTenantIdAndUser_Id(actor.getTenantId(), user.getId()).stream()
                .filter(assignment -> assignment.getStore().getId().equals(store.getId()) && assignment.isActive())
                .toList();
        if (assignments.isEmpty()) {
            throw new NotFoundException("Store assignment not found");
        }
        assignments.forEach(assignment -> assignment.revoke(actor.getId(), "Assignment removed"));
        audit(authentication, AuditAction.STORE_ASSIGNMENT_REMOVED, user.getId(), assignments, null, "USER", store.getId(), "Assignment removed");
    }

    @Transactional(readOnly = true)
    public List<AssignedStoreResponse> assignableStores(Authentication authentication) {
        User actor = actor(authentication);
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isOwner(actorRoles)) {
            return storeRepository.findByTenantIdAndActiveTrueOrderByNameAscIdAsc(actor.getTenantId()).stream()
                    .map(store -> AssignedStoreResponse.from(store, AssignmentRole.MANAGER))
                    .toList();
        }
        if (StoreAccessService.isManager(actorRoles)) {
            return activeAssignments(actor).stream()
                    .filter(assignment -> assignment.getAssignmentRole() == AssignmentRole.MANAGER)
                    .filter(assignment -> assignment.getStore().isActive())
                    .map(assignment -> AssignedStoreResponse.from(assignment.getStore(), assignment.getAssignmentRole()))
                    .toList();
        }
        throw new ForbiddenOperationException("Cashiers cannot access assignable stores");
    }

    private User save(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            String constraintName = new DatabaseConstraintErrorMapper().analyze(exception).constraintName();
            if ("uq_security_users_email".equals(constraintName)
                    || "uq_security_users_email_lower".equals(constraintName)) {
                throw duplicateEmail();
            }
            throw exception;
        }
    }

    private User actor(Authentication authentication) {
        if (storeAccessService != null) {
            return storeAccessService.currentTenantUser(authentication);
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Authenticated tenant user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Authenticated tenant user is required"));
    }

    private void requireOwner(User actor) {
        if (!StoreAccessService.isOwner(roles(actor))) {
            throw new ForbiddenOperationException("Tenant owner permission is required");
        }
        if (actor.getTenantId() == null) {
            throw new ForbiddenOperationException("Authenticated user is not assigned to a tenant");
        }
    }

    private void requireCanCreate(User actor, RoleName roleName, Authentication authentication) {
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isOwner(actorRoles)) {
            return;
        }
        if (StoreAccessService.isManager(actorRoles)
                && hasTenantPermission(authentication, PermissionCode.USER_CREATE)
                && (roleName == RoleName.CASHIER || roleName == RoleName.KITCHEN
                    || roleName == RoleName.STORE_MANAGER && hasTenantPermission(authentication, PermissionCode.MANAGER_CREATE_MANAGER))) {
            return;
        }
        throw new ForbiddenOperationException("User creation is outside the permitted employee scope");
    }

    private void requireCanModify(User actor, User target, RoleName requestedRole, Authentication authentication) {
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isOwner(actorRoles)) {
            return;
        }
        if (StoreAccessService.isManager(actorRoles)
                && managerCanSeeTarget(actor, target, authentication)
                && (requestedRole == RoleName.CASHIER || requestedRole == RoleName.KITCHEN
                    || requestedRole == RoleName.STORE_MANAGER && hasTenantPermission(authentication, PermissionCode.MANAGER_CREATE_MANAGER))) {
            return;
        }
        throw new ForbiddenOperationException("User is outside the permitted employee scope");
    }

    private User permittedTarget(User actor, UUID userId, boolean allowManagerView, Authentication authentication) {
        User target = userRepository.findByIdAndTenantId(userId, actor.getTenantId())
                .orElseThrow(() -> {
                    auditCrossTenant(actor, userId);
                    return new NotFoundException("User not found");
                });
        Set<RoleName> targetRoles = roles(target);
        if (targetRoles.stream().noneMatch(EMPLOYEE_ROLES::contains)) {
            throw new NotFoundException("User not found");
        }
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isOwner(actorRoles)) {
            return target;
        }
        if (allowManagerView && StoreAccessService.isManager(actorRoles) && managerCanSeeTarget(actor, target, authentication)) {
            return target;
        }
        auditOutOfScopeManager(actor, target.getId());
        throw new ForbiddenOperationException("User is outside the permitted employee scope");
    }

    private Specification<User> scopedSpecification(UserSearchRequest request, User actor, Set<RoleName> actorRoles, Authentication authentication) {
        RoleName requestedRole = request.role() == null ? null : employeeRole(List.of(request.role()));
        if (request.storeId() != null) {
            tenantStore(actor.getTenantId(), request.storeId());
        }
        Specification<User> spec = Specification
                .where(equalUuid("tenantId", actor.getTenantId()))
                .and(employeeOnly())
                .and(containsString("email", request.email()))
                .and(containsString("displayName", request.displayName()))
                .and(containsSearch(request.search()))
                .and(equalBoolean("enabled", request.enabled()))
                .and(equalBoolean("locked", request.locked()))
                .and(status(request.status()))
                .and(equalUuid("createdByUserId", request.createdByUserId()))
                .and(requestedRole == null ? null : hasRole(requestedRole))
                .and(activeAssignedStore(request.storeId()));
        if (StoreAccessService.isOwner(actorRoles)) {
            return spec;
        }
        if (StoreAccessService.isManager(actorRoles)) {
            Set<UUID> managedStores = activeAssignments(actor).stream()
                    .filter(assignment -> assignment.getAssignmentRole() == AssignmentRole.MANAGER)
                    .map(assignment -> assignment.getStore().getId())
                    .collect(Collectors.toUnmodifiableSet());
            if (managedStores.isEmpty()) {
                return spec.and((root, query, criteriaBuilder) -> criteriaBuilder.disjunction());
            }
            Specification<User> roleSpec = hasRole(RoleName.CASHIER);
            if (hasTenantPermissionFromRoles(actorRoles, authentication, PermissionCode.MANAGER_CREATE_MANAGER)) {
                roleSpec = roleSpec.or(hasRole(RoleName.STORE_MANAGER)).or(hasRole(RoleName.MANAGER));
            }
            return spec.and(roleSpec).and(activeAssignedStoreIn(managedStores)
                    .or(managerCreatedPending(actor.getId())));
        }
        throw new ForbiddenOperationException("User directory is not available");
    }

    private void addAssignments(User actor, User user, List<Store> stores, AssignmentRole assignmentRole) {
        stores.forEach(store -> upsertAssignment(actor, user, store, assignmentRole));
    }

    private void replaceStoreAssignments(User actor, User user, Set<UUID> storeIds, RoleName roleName, String removalReason) {
        AssignmentRole assignmentRole = assignmentRole(roleName);
        List<Store> stores = storesForAssignment(actor, storeIds);
        replaceStoreAssignments(actor, user, stores, roleName, removalReason);
    }

    private void replaceStoreAssignments(User actor, User user, List<Store> stores, RoleName roleName, String removalReason) {
        AssignmentRole assignmentRole = assignmentRole(roleName);
        Set<UUID> wanted = stores.stream().map(Store::getId).collect(Collectors.toUnmodifiableSet());
        List<UserStoreAssignment> existing = userStoreAssignmentRepository.findByTenantIdAndUser_Id(actor.getTenantId(), user.getId());
        existing.stream()
                .filter(UserStoreAssignment::isActive)
                .filter(assignment -> !wanted.contains(assignment.getStore().getId()) || assignment.getAssignmentRole() != assignmentRole)
                .forEach(assignment -> assignment.revoke(actor.getId(), cleanOptional(removalReason) == null ? "Assignment changed" : cleanOptional(removalReason)));
        stores.forEach(store -> upsertAssignment(actor, user, store, assignmentRole));
    }

    private List<Store> storesForAssignment(User actor, Set<UUID> storeIds) {
        Set<UUID> manageableStores = manageableStoreIds(actor);
        boolean owner = StoreAccessService.isOwner(roles(actor));
        return storeIds.stream()
                .map(storeId -> {
                    Store store = tenantStore(actor.getTenantId(), storeId);
                    if (!owner && !manageableStores.contains(store.getId())) {
                        auditOutOfScopeStore(actor, store.getId());
                        throw new ForbiddenOperationException("Store is outside the manager's assignment scope");
                    }
                    return store;
                })
                .toList();
    }

    private void upsertAssignment(User actor, User user, Store store, AssignmentRole assignmentRole) {
        if (!store.getTenantId().equals(actor.getTenantId()) || !actor.getTenantId().equals(user.getTenantId())) {
            auditCrossTenant(actor, user.getId());
            throw new BadRequestException("User and store must belong to the authenticated tenant");
        }
        UserStoreAssignment assignment = userStoreAssignmentRepository
                .findByTenantIdAndUser_IdAndStore_IdAndAssignmentRole(actor.getTenantId(), user.getId(), store.getId(), assignmentRole)
                .orElse(null);
        if (assignment == null) {
            userStoreAssignmentRepository.save(new UserStoreAssignment(actor.getTenantId(), user, store, assignmentRole, actor.getId()));
            audit(null, AuditAction.STORE_ASSIGNMENT_ADDED, user.getId(), null, store.getId(), "USER", store.getId(), null);
            return;
        }
        if (!assignment.isActive()) {
            assignment.reactivate(assignmentRole, actor.getId());
            audit(null, AuditAction.STORE_ASSIGNMENT_ADDED, user.getId(), null, store.getId(), "USER", store.getId(), null);
        }
    }

    private void replaceRegisterAssignments(User actor, User user, Set<UUID> registerIds, Set<UUID> storeIds) {
        validateRegisterAssignments(actor, registerIds, storeIds);
        List<UserRegisterAssignment> existing = userRegisterAssignmentRepository.findByUser(user);
        Set<UUID> existingRegisterIds = existing.stream()
                .map(assignment -> assignment.getRegister().getId())
                .collect(Collectors.toUnmodifiableSet());
        List<UserRegisterAssignment> removed = existing.stream()
                .filter(assignment -> !registerIds.contains(assignment.getRegister().getId()))
                .toList();
        if (!removed.isEmpty()) {
            userRegisterAssignmentRepository.deleteAll(removed);
        }
        registerIds.stream()
                .filter(registerId -> !existingRegisterIds.contains(registerId))
                .map(registerId -> registerRepository.findById(registerId)
                        .orElseThrow(() -> new NotFoundException("Register not found")))
                .forEach(register -> userRegisterAssignmentRepository.save(new UserRegisterAssignment(user, register)));
    }

    private void validateRegisterAssignments(User actor, Set<UUID> registerIds, Set<UUID> storeIds) {
        List<Register> registers = registerIds.stream()
                .map(registerId -> registerRepository.findById(registerId)
                        .orElseThrow(() -> new NotFoundException("Register not found")))
                .toList();
        registers.forEach(register -> {
            UUID registerStoreId = register.getStore().getId();
            if (!storeIds.isEmpty() && !storeIds.contains(registerStoreId)) {
                throw new BadRequestException("registerIds must belong to assigned stores");
            }
            tenantStore(actor.getTenantId(), registerStoreId);
        });
    }

    private void replaceRoles(User user, Collection<RoleName> roleNames) {
        List<Role> roles = roleNames.stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new NotFoundException("Role not found: " + roleName)))
                .toList();
        userRoleRepository.deleteByUser(user);
        roles.forEach(role -> userRoleRepository.save(new UserRole(user, role)));
    }

    private UserResponse response(User user) {
        List<RoleName> roles = roles(user).stream().sorted().toList();
        List<UserStoreAssignmentResponse> storeAssignments = userStoreAssignmentRepository
                .findByTenantIdAndUser_Id(user.getTenantId(), user.getId()).stream()
                .map(UserStoreAssignmentResponse::from)
                .toList();
        List<UUID> storeIds = storeAssignments.stream()
                .filter(UserStoreAssignmentResponse::active)
                .map(UserStoreAssignmentResponse::storeId)
                .distinct()
                .sorted()
                .toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEnabled(),
                user.isLocked(),
                roles,
                storeIds,
                registerIds(user),
                status(user),
                storeAssignments,
                user.getCreatedByUserId(),
                user.getCreatedByRole(),
                user.getUpdatedByUserId(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion());
    }

    private List<UserResponse> responses(List<User> users, UUID tenantId) {
        if (users.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RoleName>> rolesByUser = userRoleRepository.findByUserIn(users).stream()
                .collect(Collectors.groupingBy(
                        userRole -> userRole.getUser().getId(),
                        Collectors.mapping(userRole -> userRole.getRole().getName(), Collectors.toList())));
        Map<UUID, List<UserStoreAssignmentResponse>> assignmentsByUser = userStoreAssignmentRepository
                .findByTenantIdAndUserInAndActiveTrue(tenantId, users).stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getUser().getId(),
                        Collectors.mapping(UserStoreAssignmentResponse::from, Collectors.toList())));
        return users.stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.isEnabled(),
                        user.isLocked(),
                        rolesByUser.getOrDefault(user.getId(), List.of()).stream().sorted().toList(),
                        assignmentsByUser.getOrDefault(user.getId(), List.of()).stream()
                                .map(UserStoreAssignmentResponse::storeId).distinct().sorted().toList(),
                        registerIds(user),
                        status(user),
                        assignmentsByUser.getOrDefault(user.getId(), List.of()),
                        user.getCreatedByUserId(),
                        user.getCreatedByRole(),
                        user.getUpdatedByUserId(),
                        user.getCreatedAt(),
                        user.getUpdatedAt(),
                        user.getVersion()))
                .toList();
    }

    private List<UUID> registerIds(User user) {
        return userRegisterAssignmentRepository.findByUser(user).stream()
                .map(assignment -> assignment.getRegister().getId())
                .sorted()
                .toList();
    }

    private List<UserStoreAssignment> activeStoreAssignments(User user) {
        return userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(user.getTenantId(), user);
    }

    private List<UserStoreAssignment> activeAssignments(User user) {
        return userStoreAssignmentRepository.findByTenantIdAndUserAndActiveTrue(user.getTenantId(), user);
    }

    private boolean sharesManagedStore(User manager, User target) {
        Set<UUID> managerStoreIds = manageableStoreIds(manager);
        if (managerStoreIds.isEmpty()) {
            return false;
        }
        return activeAssignments(target).stream()
                .anyMatch(assignment -> managerStoreIds.contains(assignment.getStore().getId()));
    }

    private Set<UUID> manageableStoreIds(User manager) {
        return activeAssignments(manager).stream()
                .filter(assignment -> assignment.getAssignmentRole() == AssignmentRole.MANAGER)
                .map(assignment -> assignment.getStore().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Store tenantStore(UUID tenantId, UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findByIdAndTenantId(storeId, tenantId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Set<RoleName> roles(User user) {
        return userRoleRepository.findByUser(user).stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean managerCanSeeTarget(User actor, User target, Authentication authentication) {
        Set<RoleName> targetRoles = roles(target);
        boolean visibleRole = targetRoles.contains(RoleName.CASHIER) || targetRoles.contains(RoleName.KITCHEN)
                || (targetRoles.contains(RoleName.STORE_MANAGER) || targetRoles.contains(RoleName.MANAGER))
                && hasTenantPermission(authentication, PermissionCode.MANAGER_CREATE_MANAGER);
        if (!visibleRole) {
            return false;
        }
        if (sharesManagedStore(actor, target)) {
            return true;
        }
        return actor.getId().equals(target.getCreatedByUserId())
                && !target.isEnabled()
                && activeAssignments(target).isEmpty();
    }

    private RoleName actorCreatorRole(User actor) {
        Set<RoleName> actorRoles = roles(actor);
        if (StoreAccessService.isOwner(actorRoles)) {
            return RoleName.TENANT_OWNER;
        }
        if (StoreAccessService.isManager(actorRoles)) {
            return RoleName.STORE_MANAGER;
        }
        throw new ForbiddenOperationException("Authenticated user cannot create merchant employees");
    }

    private RoleName primaryEmployeeRole(User user) {
        Set<RoleName> roles = roles(user);
        if (roles.contains(RoleName.STORE_MANAGER) || roles.contains(RoleName.MANAGER)) {
            return RoleName.STORE_MANAGER;
        }
        if (roles.contains(RoleName.CASHIER)) {
            return RoleName.CASHIER;
        }
        if (roles.contains(RoleName.KITCHEN)) {
            return RoleName.KITCHEN;
        }
        throw new ForbiddenOperationException("User is outside the permitted employee scope");
    }

    private RoleName employeeRole(Collection<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BadRequestException("Exactly one employee role is required");
        }
        Set<RoleName> normalized = roleNames.stream().map(role -> switch (role) {
            case STORE_MANAGER, MANAGER -> RoleName.STORE_MANAGER;
            case CASHIER -> RoleName.CASHIER;
            case KITCHEN -> RoleName.KITCHEN;
            default -> throw new BadRequestException("Merchant employee APIs only support STORE_MANAGER, CASHIER and KITCHEN roles");
        }).collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.size() != 1) {
            throw new BadRequestException("Exactly one employee role is required");
        }
        return normalized.iterator().next();
    }

    private RoleName employeeRoleForAssignment(AssignmentRole assignmentRole) {
        if (assignmentRole == AssignmentRole.MANAGER) {
            return RoleName.STORE_MANAGER;
        }
        if (assignmentRole == AssignmentRole.CASHIER) {
            return RoleName.CASHIER;
        }
        if (assignmentRole == AssignmentRole.KITCHEN) {
            return RoleName.KITCHEN;
        }
        throw new BadRequestException("assignmentRole is required");
    }

    private void requireRoleCompatible(User user, RoleName roleName) {
        RoleName current = primaryEmployeeRole(user);
        if (current != roleName) {
            throw new BadRequestException("assignmentRole must match the user's merchant role");
        }
    }

    private AssignmentRole assignmentRole(RoleName roleName) {
        return roleName == RoleName.STORE_MANAGER ? AssignmentRole.MANAGER
                : roleName == RoleName.KITCHEN ? AssignmentRole.KITCHEN : AssignmentRole.CASHIER;
    }

    private void validateRoleStoreCapabilities(RoleName roleName, List<Store> stores) {
        if (roleName == RoleName.KITCHEN && stores.stream().anyMatch(store -> !store.getCapabilities().contains(StoreCapability.FOOD_SERVICE))) {
            throw new BadRequestException("Kitchen users may only be assigned to FOOD_SERVICE stores");
        }
    }

    private String status(User user) {
        if (user.isLocked()) {
            return "LOCKED";
        }
        return user.isEnabled() ? "ACTIVE" : "DISABLED";
    }

    private AuditAction createAction(RoleName roleName) {
        return roleName == RoleName.STORE_MANAGER ? AuditAction.USER_CREATED : AuditAction.USER_CREATED;
    }

    private Specification<User> employeeOnly() {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            var subquery = query.subquery(UUID.class);
            var userRole = subquery.from(UserRole.class);
            subquery.select(userRole.get("user").get("id"))
                    .where(
                            criteriaBuilder.equal(userRole.get("user"), root),
                            userRole.get("role").get("name").in(EMPLOYEE_ROLES));
            return criteriaBuilder.exists(subquery);
        };
    }

    private Specification<User> hasRole(RoleName role) {
        return (root, query, criteriaBuilder) -> {
            var subquery = query.subquery(UUID.class);
            var userRole = subquery.from(UserRole.class);
            subquery.select(userRole.get("user").get("id"))
                    .where(
                            criteriaBuilder.equal(userRole.get("user"), root),
                            criteriaBuilder.equal(userRole.get("role").get("name"), role));
            return criteriaBuilder.exists(subquery);
        };
    }

    private Specification<User> activeAssignedStore(UUID storeId) {
        if (storeId == null) {
            return null;
        }
        return activeAssignedStoreIn(Set.of(storeId));
    }

    private Specification<User> activeAssignedStoreIn(Set<UUID> storeIds) {
        return (root, query, criteriaBuilder) -> {
            var subquery = query.subquery(UUID.class);
            var assignment = subquery.from(UserStoreAssignment.class);
            subquery.select(assignment.get("user").get("id"))
                    .where(
                            criteriaBuilder.equal(assignment.get("user"), root),
                            assignment.get("store").get("id").in(storeIds),
                            criteriaBuilder.isTrue(assignment.get("active")));
            return criteriaBuilder.exists(subquery);
        };
    }

    private Specification<User> managerCreatedPending(UUID actorId) {
        return (root, query, criteriaBuilder) -> {
            var assignmentSubquery = query.subquery(UUID.class);
            var assignment = assignmentSubquery.from(UserStoreAssignment.class);
            assignmentSubquery.select(assignment.get("user").get("id"))
                    .where(
                            criteriaBuilder.equal(assignment.get("user"), root),
                            criteriaBuilder.isTrue(assignment.get("active")));
            return criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("createdByUserId"), actorId),
                    criteriaBuilder.isFalse(root.get("enabled")),
                    criteriaBuilder.not(criteriaBuilder.exists(assignmentSubquery)));
        };
    }

    private Specification<User> status(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> (root, query, cb) -> cb.and(cb.isTrue(root.get("enabled")), cb.isFalse(root.get("locked")));
            case "DISABLED", "ARCHIVED" -> equalBoolean("enabled", false);
            case "LOCKED" -> equalBoolean("locked", true);
            case "INVITED", "PENDING" -> equalBoolean("enabled", false);
            default -> throw new BadRequestException("Unsupported user status filter");
        };
    }

    private static Specification<User> containsSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern));
    }

    private static Specification<User> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    private static Specification<User> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<User> equalUuid(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private void requireCurrentVersion(User user, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != user.getVersion()) {
            throw new ConflictException("User was modified by another transaction");
        }
    }

    private void audit(Authentication authentication, AuditAction action, UUID userId, Object before, Object after,
            String entityType, UUID storeId, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                entityType,
                userId,
                storeId,
                null,
                before,
                after,
                reason));
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).map(User::getId).orElse(null);
    }

    private boolean hasTenantPermission(Authentication authentication, PermissionCode permission) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.name().equals(authority.getAuthority()));
    }

    private boolean hasTenantPermissionFromRoles(Set<RoleName> actorRoles, Authentication authentication, PermissionCode permission) {
        return StoreAccessService.isOwner(actorRoles) || hasTenantPermission(authentication, permission);
    }

    private void auditCrossTenant(User actor, UUID targetUserId) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.CROSS_TENANT_ACCESS_DENIED,
                "USER",
                targetUserId,
                null,
                null,
                null,
                null,
                "Tenant-scoped user access denied"));
    }

    private void auditOutOfScopeManager(User actor, UUID targetUserId) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.OUT_OF_SCOPE_MANAGER_USER_ACCESS_DENIED,
                "USER",
                targetUserId,
                null,
                null,
                null,
                null,
                "Manager user visibility denied"));
    }

    private void auditOutOfScopeStore(User actor, UUID storeId) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.OUT_OF_SCOPE_STORE_ASSIGNMENT_DENIED,
                "STORE",
                storeId,
                storeId,
                null,
                null,
                null,
                "Manager store assignment scope denied"));
    }

    private static Set<UUID> ids(Collection<UUID> ids) {
        if (ids == null) {
            return Set.of();
        }
        if (ids.stream().anyMatch(id -> id == null)) {
            throw new BadRequestException("Assignment ids cannot be null");
        }
        return new LinkedHashSet<>(ids);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ConflictException duplicateEmail() {
        return new ConflictException("User email is already registered");
    }
}
