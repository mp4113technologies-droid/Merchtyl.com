package com.merchtyl.registersession;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.eod.BusinessDay;
import com.merchtyl.eod.BusinessDayService;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.register.RegisterCapabilityService;
import com.merchtyl.register.RegisterType;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRegisterAssignmentRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.RolePermissionRepository;
import com.merchtyl.security.UserRoleRepository;
import com.merchtyl.security.UserStoreAssignmentRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RegisterSessionService {
    private static final Logger log = LoggerFactory.getLogger(RegisterSessionService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MONEY_SCALE = 2;
    private static final java.util.List<RegisterSessionStatus> CURRENT_STATUSES = java.util.List.of(
            RegisterSessionStatus.OPEN, RegisterSessionStatus.CLOSING);

    private final RegisterSessionRepository registerSessionRepository;
    private final StoreRepository storeRepository;
    private final RegisterRepository registerRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final UserRegisterAssignmentRepository userRegisterAssignmentRepository;
    private final AuditService auditService;
    private final CashLedgerService cashLedgerService;
    private final RegisterSessionProperties properties;
    private final Clock clock;
    private final StoreAccessService storeAccessService;
    private final RegisterDeviceEnforcementProperties deviceEnforcementProperties;
    @Autowired(required = false)
    private RegisterSessionOperatorHistoryRepository operatorHistoryRepository;
    @Autowired(required = false)
    private UserStoreAssignmentRepository userStoreAssignmentRepository;
    @Autowired(required = false)
    private RefreshTokenService refreshTokenService;
    @Autowired(required = false)
    private UserRoleRepository userRoleRepository;
    @Autowired(required = false)
    private RolePermissionRepository rolePermissionRepository;
    @Autowired(required = false)
    private BusinessDayService businessDayService;
    @Autowired(required = false)
    private RegisterCapabilityService registerCapabilityService;

    @Autowired
    public RegisterSessionService(
            RegisterSessionRepository registerSessionRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            DeviceRepository deviceRepository,
            UserRepository userRepository,
            UserRegisterAssignmentRepository userRegisterAssignmentRepository,
            AuditService auditService,
            CashLedgerService cashLedgerService,
            RegisterSessionProperties properties,
            StoreAccessService storeAccessService,
            RegisterDeviceEnforcementProperties deviceEnforcementProperties) {
        this(
                registerSessionRepository,
                storeRepository,
                registerRepository,
                deviceRepository,
                userRepository,
                userRegisterAssignmentRepository,
                auditService,
                cashLedgerService,
                properties,
                Clock.systemUTC(),
                storeAccessService,
                deviceEnforcementProperties);
    }

    RegisterSessionService(
            RegisterSessionRepository registerSessionRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            DeviceRepository deviceRepository,
            UserRepository userRepository,
            UserRegisterAssignmentRepository userRegisterAssignmentRepository,
            AuditService auditService,
            CashLedgerService cashLedgerService,
            RegisterSessionProperties properties,
            Clock clock,
            StoreAccessService storeAccessService,
            RegisterDeviceEnforcementProperties deviceEnforcementProperties) {
        this.registerSessionRepository = registerSessionRepository;
        this.storeRepository = storeRepository;
        this.registerRepository = registerRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.userRegisterAssignmentRepository = userRegisterAssignmentRepository;
        this.auditService = auditService;
        this.cashLedgerService = cashLedgerService;
        this.properties = properties;
        this.clock = clock;
        this.storeAccessService = storeAccessService;
        this.deviceEnforcementProperties = deviceEnforcementProperties == null
                ? new RegisterDeviceEnforcementProperties()
                : deviceEnforcementProperties;
    }

    RegisterSessionService(
            RegisterSessionRepository registerSessionRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            DeviceRepository deviceRepository,
            UserRepository userRepository,
            UserRegisterAssignmentRepository userRegisterAssignmentRepository,
            AuditService auditService,
            CashLedgerService cashLedgerService,
            RegisterSessionProperties properties,
            Clock clock) {
        this(registerSessionRepository, storeRepository, registerRepository, deviceRepository, userRepository,
                userRegisterAssignmentRepository, auditService, cashLedgerService, properties, clock, null, null);
    }

    @Transactional
    public RegisterSessionResponse open(RegisterSessionOpenRequest request, Authentication authentication) {
        boolean enforcementEnabled = deviceEnforcementProperties.isEnabled();
        log.info("register_event event=REGISTER_OPEN_REQUESTED store_id={} register_id={} device_id={} device_enforcement_enabled={}",
                request.storeId(), request.registerId(), request.deviceId(), enforcementEnabled);
        try {
            return doOpen(request, authentication, enforcementEnabled);
        } catch (RuntimeException exception) {
            log.warn("register_event event=REGISTER_OPEN_FAILED store_id={} register_id={} device_id={} device_enforcement_enabled={} reason={}",
                    request.storeId(), request.registerId(), request.deviceId(), enforcementEnabled, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private RegisterSessionResponse doOpen(RegisterSessionOpenRequest request, Authentication authentication, boolean enforcementEnabled) {
        Store store = findStore(request.storeId());
        Register register = findRegister(request.registerId(), properties.isSingleOpenPerRegister());
        if (enforcementEnabled && request.deviceId() == null) {
            throw new RegisterDeviceRequiredException();
        }
        Device device = request.deviceId() == null
                ? null
                : findDevice(request.deviceId(), properties.isSingleOpenPerDevice());
        User cashier = currentUser(authentication);

        if (storeAccessService != null) {
            storeAccessService.requireStoreAccess(authentication, store.getId());
        }
        validateActive(store, register);
        validateRelationships(store, register);
        RegisterType registerType = register.getType() == null ? RegisterType.RETAIL : register.getType();
        if (registerCapabilityService != null) {
            registerCapabilityService.requireEnabled(store, registerType);
        }
        String requiredPosPermission = registerType == RegisterType.FOOD_SERVICE ? "FOOD_POS_ACCESS" : "POS_ACCESS";
        if (!hasPermission(cashier, requiredPosPermission)) {
            throw new ForbiddenOperationException("REGISTER_TYPE_NOT_ALLOWED: User is not permitted to use this register type.");
        }
        BusinessDay businessDay = businessDayService == null
                ? null
                : businessDayService.requireOpenBusinessDayForUpdate(store.getId());
        if (registerSessionRepository.findFirstByRegister_IdAndStatusInOrderByOpenedAtDesc(
                register.getId(), CURRENT_STATUSES).isPresent()) {
            throw new ConflictException("REGISTER_OPENING_CASH_IMMUTABLE");
        }
        BigDecimal openingCash = normalizeOpeningCash(request.openingCash());
        if (device != null) {
            validateDevice(store, register, device);
        }
        validateCashier(cashier, register, authentication);
        if (isStoreOperator(authentication)
                && registerSessionRepository.existsByAssignedCashier_IdAndStatusIn(cashier.getId(), CURRENT_STATUSES)) {
            throw new ConflictException("CASHIER_ALREADY_HAS_OPEN_SESSION");
        }
        enforceSingleOpenSession(register.getId(), device == null ? null : device.getId());

        RegisterSession session = new RegisterSession(
                store,
                register,
                businessDay,
                device,
                cashier,
                openingCash,
                Instant.now(clock));
        RegisterSession saved = save(session);
        cashLedgerService.appendOpeningFloat(saved, cashier);
        RegisterSessionResponse response = RegisterSessionResponse.from(saved, cashLedgerService.expectedCash(saved));
        auditService.record(new CreateAuditRecordCommand(
                cashier.getId(),
                AuditAction.REGISTER_SESSION_OPENED,
                "REGISTER_SESSION",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                null));
        log.info("register_event event=REGISTER_OPENED tenant_id={} store_id={} register_id={} user_id={} device_id={} device_enforcement_enabled={}",
                cashier.getTenantId(), response.storeId(), response.registerId(), cashier.getId(), response.deviceId(), enforcementEnabled);
        return response;
    }

    @Transactional(readOnly = true)
    public RegisterSessionResponse current(UUID deviceId, String deviceIdentifier, Authentication authentication) {
        User actor = currentUser(authentication);
        return currentSession(deviceId, deviceIdentifier, authentication)
                .filter(session -> canViewCurrent(session, actor, authentication))
                .map(session -> RegisterSessionResponse.from(session, cashLedgerService.breakdown(session)))
                .orElse(null);
    }

    @Transactional
    public RegisterSessionResponse transfer(UUID id, RegisterSessionTransferRequest request, Authentication authentication) {
        RegisterSession session = registerSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        User actor = currentUser(authentication);
        if (storeAccessService != null) {
            storeAccessService.requireStoreManagement(authentication, session.getStore().getId());
        }
        requireVersion(session, request.version());
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("REGISTER_SESSION_NOT_OPEN");
        }
        User nextOperator = userRepository.findByIdAndTenantId(request.newOperatorUserId(), actor.getTenantId())
                .filter(User::isEnabled)
                .filter(user -> !user.isLocked())
                .orElseThrow(() -> new ForbiddenOperationException("REGISTER_SESSION_ACCESS_DENIED"));
        if (userStoreAssignmentRepository == null || !userStoreAssignmentRepository
                .existsByTenantIdAndUser_IdAndStore_IdAndActiveTrue(actor.getTenantId(), nextOperator.getId(), session.getStore().getId())) {
            throw new ForbiddenOperationException("REGISTER_OPERATOR_NOT_ASSIGNED_TO_STORE");
        }
        if (registerSessionRepository.existsByAssignedCashier_IdAndStatusIn(nextOperator.getId(), CURRENT_STATUSES)) {
            throw new ConflictException("REGISTER_OPERATOR_ALREADY_HAS_SESSION");
        }
        User previousOperator = session.getAssignedCashier();
        String reason = cleanRequired(request.reason(), "reason");
        session.transferTo(nextOperator);
        RegisterSession saved = saveClosing(session);
        if (operatorHistoryRepository != null) {
            operatorHistoryRepository.save(new RegisterSessionOperatorHistory(
                    saved, previousOperator, nextOperator, actor, reason, Instant.now(clock)));
        }
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.REGISTER_OPERATOR_TRANSFERRED,
                "REGISTER_SESSION", saved.getId(), saved.getStore().getId(), saved.getRegister().getId(),
                Map.of("previousOperatorUserId", previousOperator.getId()),
                Map.of("newOperatorUserId", nextOperator.getId()), reason));
        log.info("register_event event=REGISTER_SESSION_TRANSFERRED tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} previous_operator_user_id={} operator_user_id={}",
                actor.getTenantId(), saved.getStore().getId(), saved.getRegister().getId(), saved.getId(), actor.getId(), previousOperator.getId(), nextOperator.getId());
        return RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
    }

    @Transactional
    public RegisterSessionResponse override(UUID id, RegisterSessionOverrideRequest request, Authentication authentication) {
        RegisterSession session = registerSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        User actor = currentUser(authentication);
        log.info("register_event event=REGISTER_SESSION_OVERRIDE_REQUESTED tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} operator_user_id={}",
                actor.getTenantId(), session.getStore().getId(), session.getRegister().getId(), session.getId(), actor.getId(), session.getAssignedCashier().getId());
        if (!isOwner(authentication) && !isManager(authentication)) {
            throw new ForbiddenOperationException("REGISTER_SESSION_OVERRIDE_NOT_ALLOWED");
        }
        if (storeAccessService != null) {
            storeAccessService.requireStoreManagement(authentication, session.getStore().getId());
        }
        requireVersion(session, request.version());
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("REGISTER_SESSION_NOT_OPEN");
        }
        User previousOperator = session.getAssignedCashier();
        if (previousOperator.getId().equals(actor.getId())) {
            return RegisterSessionResponse.from(session, cashLedgerService.breakdown(session));
        }
        String reason = cleanRequired(request.reason(), "reason");
        session.transferTo(actor);
        RegisterSession saved = saveClosing(session);
        if (operatorHistoryRepository != null) {
            operatorHistoryRepository.save(new RegisterSessionOperatorHistory(
                    saved, previousOperator, actor, actor, reason, Instant.now(clock), "OVERRIDE"));
        }
        if (refreshTokenService != null) {
            refreshTokenService.revokeActiveTokensForUser(previousOperator, Instant.now(clock));
        }
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.REGISTER_SESSION_OVERRIDDEN,
                "REGISTER_SESSION", saved.getId(), saved.getStore().getId(), saved.getRegister().getId(),
                Map.of("previousOperatorUserId", previousOperator.getId()),
                Map.of("newOperatorUserId", actor.getId()), reason));
        log.info("register_event event=REGISTER_SESSION_OVERRIDDEN tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} previous_operator_user_id={} operator_user_id={}",
                actor.getTenantId(), saved.getStore().getId(), saved.getRegister().getId(), saved.getId(), actor.getId(), previousOperator.getId(), actor.getId());
        log.info("register_event event=REGISTER_OPERATOR_FORCED_LOGOUT tenant_id={} session_id={} operator_user_id={} actor_user_id={}",
                actor.getTenantId(), saved.getId(), previousOperator.getId(), actor.getId());
        return RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
    }

    @Transactional
    public RegisterSessionResponse release(UUID id, RegisterSessionReleaseRequest request, Authentication authentication) {
        RegisterSession session = registerSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        User actor = currentUser(authentication);
        if (!isOwner(authentication) && !isManager(authentication)) {
            throw new ForbiddenOperationException("REGISTER_SESSION_RELEASE_NOT_ALLOWED");
        }
        if (storeAccessService != null) {
            storeAccessService.requireStoreManagement(authentication, session.getStore().getId());
        }
        requireVersion(session, request.version());
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("REGISTER_SESSION_NOT_OPEN");
        }
        if (!session.getAssignedCashier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("REGISTER_SESSION_RELEASE_NOT_ALLOWED");
        }
        User cashier = userRepository.findByIdAndTenantId(request.cashierUserId(), actor.getTenantId())
                .filter(User::isEnabled)
                .filter(user -> !user.isLocked())
                .orElseThrow(() -> new ForbiddenOperationException("REGISTER_SESSION_ACCESS_DENIED"));
        if (!hasRole(cashier, RoleName.CASHIER) || !hasPermission(cashier, "POS_ACCESS")) {
            throw new ForbiddenOperationException("REGISTER_SESSION_ACCESS_DENIED");
        }
        if (userStoreAssignmentRepository == null || !userStoreAssignmentRepository
                .existsByTenantIdAndUser_IdAndStore_IdAndActiveTrue(actor.getTenantId(), cashier.getId(), session.getStore().getId())) {
            throw new ForbiddenOperationException("REGISTER_OPERATOR_NOT_ASSIGNED_TO_STORE");
        }
        if (registerSessionRepository.existsByAssignedCashier_IdAndStatusIn(cashier.getId(), CURRENT_STATUSES)) {
            throw new ConflictException("REGISTER_OPERATOR_ALREADY_HAS_SESSION");
        }
        String reason = cleanRequired(request.reason(), "reason");
        User previousOperator = session.getAssignedCashier();
        session.transferTo(cashier);
        RegisterSession saved = saveClosing(session);
        if (operatorHistoryRepository != null) {
            operatorHistoryRepository.save(new RegisterSessionOperatorHistory(
                    saved, previousOperator, cashier, actor, reason, Instant.now(clock), "RELEASE"));
        }
        if (refreshTokenService != null) {
            refreshTokenService.revokeActiveTokensForUser(previousOperator, Instant.now(clock));
        }
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.REGISTER_OPERATOR_RELEASED,
                "REGISTER_SESSION", saved.getId(), saved.getStore().getId(), saved.getRegister().getId(),
                Map.of("previousOperatorUserId", previousOperator.getId()),
                Map.of("newOperatorUserId", cashier.getId()), reason));
        log.info("register_event event=REGISTER_SESSION_RELEASED tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} previous_operator_user_id={} operator_user_id={}",
                actor.getTenantId(), saved.getStore().getId(), saved.getRegister().getId(), saved.getId(), actor.getId(), previousOperator.getId(), cashier.getId());
        return RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
    }

    @Transactional
    public RegisterSessionResponse startClosing(UUID id, RegisterSessionTransitionRequest request, Authentication authentication) {
        RegisterSession session = registerSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        User actor = currentUser(authentication);
        if (storeAccessService != null) {
            storeAccessService.requireStoreAccess(authentication, session.getStore().getId());
        }
        validateCanClose(session, actor, authentication);
        requireVersion(session, request.version());
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("REGISTER_SESSION_NOT_OPEN");
        }
        session.startClosing();
        RegisterSession saved = saveClosing(session);
        RegisterSessionResponse response = RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.REGISTER_SESSION_CLOSING_STARTED,
                "REGISTER_SESSION", saved.getId(), saved.getStore().getId(), saved.getRegister().getId(), null,
                response, null));
        return response;
    }

    @Transactional
    public RegisterSessionResponse cancelClosing(UUID id, RegisterSessionTransitionRequest request, Authentication authentication) {
        RegisterSession session = registerSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        User actor = currentUser(authentication);
        if (storeAccessService != null) {
            storeAccessService.requireStoreAccess(authentication, session.getStore().getId());
        }
        validateCanClose(session, actor, authentication);
        requireVersion(session, request.version());
        if (session.getStatus() != RegisterSessionStatus.CLOSING) {
            throw new ConflictException("REGISTER_SESSION_NOT_CLOSING");
        }
        session.cancelClosing();
        RegisterSession saved = saveClosing(session);
        RegisterSessionResponse response = RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.REGISTER_SESSION_CLOSING_CANCELLED,
                "REGISTER_SESSION", saved.getId(), saved.getStore().getId(), saved.getRegister().getId(), null,
                response, null));
        return response;
    }

    @Transactional
    public RegisterSessionResponse close(UUID id, RegisterSessionCloseRequest request, Authentication authentication) {
        RegisterSession session = registerSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        User closingUser = currentUser(authentication);
        if (storeAccessService != null) {
            storeAccessService.requireStoreAccess(authentication, session.getStore().getId());
        }
        validateCanClose(session, closingUser, authentication);
        requireVersion(session, request.version());
        if (session.getStatus() != RegisterSessionStatus.CLOSING) {
            throw new ConflictException("REGISTER_SESSION_NOT_CLOSING");
        }
        BigDecimal countedCash = normalizeCountedCash(request.countedCash());
        CashLedgerBreakdownResponse reconciliation = cashLedgerService.breakdown(session);
        session.close(countedCash, reconciliation.expectedCash(), closingUser, Instant.now(clock));
        RegisterSession saved = saveClosing(session);
        if (refreshTokenService != null && !session.getAssignedCashier().getId().equals(closingUser.getId())) {
            refreshTokenService.revokeActiveTokensForUser(session.getAssignedCashier(), Instant.now(clock));
            log.info("register_event event=REGISTER_OPERATOR_FORCED_LOGOUT tenant_id={} session_id={} operator_user_id={} actor_user_id={}",
                    closingUser.getTenantId(), saved.getId(), session.getAssignedCashier().getId(), closingUser.getId());
        }
        RegisterSessionResponse response = RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
        auditService.record(new CreateAuditRecordCommand(
                closingUser.getId(),
                AuditAction.REGISTER_SESSION_CLOSED,
                "REGISTER_SESSION",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                null));
        log.info("register_event event=REGISTER_SESSION_CLOSED tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} operator_user_id={}",
                closingUser.getTenantId(), response.storeId(), response.registerId(), response.id(), closingUser.getId(), session.getAssignedCashier().getId());
        return response;
    }

    @Transactional
    public RegisterSessionResponse forceClose(UUID id, RegisterSessionForceCloseRequest request, Authentication authentication) {
        RegisterSession session = findSession(id);
        User closingUser = currentUser(authentication);
        if (storeAccessService != null) {
            storeAccessService.requireStoreManagement(authentication, session.getStore().getId());
        }
        requireVersion(session, request.version());
        if (session.getStatus() == RegisterSessionStatus.CLOSED || session.getStatus() == RegisterSessionStatus.FORCE_CLOSED) {
            throw new ConflictException("Register session is already closed");
        }
        BigDecimal countedCash = normalizeCountedCash(request.countedCash());
        String reason = cleanRequired(request.reason(), "reason");
        CashLedgerBreakdownResponse reconciliation = cashLedgerService.breakdown(session);
        session.forceClose(countedCash, reconciliation.expectedCash(), closingUser, Instant.now(clock), reason);
        RegisterSession saved = saveClosing(session);
        if (refreshTokenService != null && !session.getAssignedCashier().getId().equals(closingUser.getId())) {
            refreshTokenService.revokeActiveTokensForUser(session.getAssignedCashier(), Instant.now(clock));
            log.info("register_event event=REGISTER_OPERATOR_FORCED_LOGOUT tenant_id={} session_id={} operator_user_id={} actor_user_id={}",
                    closingUser.getTenantId(), saved.getId(), session.getAssignedCashier().getId(), closingUser.getId());
        }
        RegisterSessionResponse response = RegisterSessionResponse.from(saved, cashLedgerService.breakdown(saved));
        auditService.record(new CreateAuditRecordCommand(
                closingUser.getId(),
                AuditAction.REGISTER_SESSION_FORCE_CLOSED,
                "REGISTER_SESSION",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                reason));
        log.info("register_event event=REGISTER_SESSION_FORCE_CLOSED tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} operator_user_id={}",
                closingUser.getTenantId(), response.storeId(), response.registerId(), response.id(), closingUser.getId(), session.getAssignedCashier().getId());
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<RegisterSessionResponse> search(RegisterSessionSearchRequest request, Authentication authentication) {
        User actor = currentUser(authentication);
        Specification<RegisterSession> scoped = specification(request)
                .and(equalTenant(actor.getTenantId()));
        if (!isOwner(authentication)) {
            var stores = storeAccessService == null ? java.util.Set.<UUID>of()
                    : isManager(authentication)
                    ? storeAccessService.getActiveManagedStoreIds(actor.getId())
                    : storeAccessService.getActiveAssignedStoreIds(actor.getId());
            scoped = scoped.and(storeIn(stores));
        }
        if (isStoreOperator(authentication)) {
            scoped = scoped.and(equalReference("assignedCashier", actor.getId()));
        }
        return search(request, scoped);
    }

    private PageResponse<RegisterSessionResponse> search(
            RegisterSessionSearchRequest request,
            Specification<RegisterSession> specification) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = registerSessionRepository.findAll(
                specification,
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "openedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<UUID, CashLedgerBreakdownResponse> breakdowns = cashLedgerService.breakdowns(page.getContent());
        return new PageResponse<>(
                page.getContent().stream()
                        .map(session -> RegisterSessionResponse.from(session, breakdowns.get(session.getId())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    public PageResponse<RegisterSessionResponse> search(RegisterSessionSearchRequest request) {
        return search(request, specification(request));
    }

    private java.util.Optional<RegisterSession> currentSession(UUID deviceId, String deviceIdentifier, Authentication authentication) {
        if (deviceId != null) {
            return registerSessionRepository.findFirstByDevice_IdAndStatusInOrderByOpenedAtDesc(deviceId, CURRENT_STATUSES);
        }
        if (deviceIdentifier != null && !deviceIdentifier.isBlank()) {
            var deviceSession = registerSessionRepository.findFirstByDevice_DeviceIdentifierIgnoreCaseAndStatusInOrderByOpenedAtDesc(
                    deviceIdentifier.trim(), CURRENT_STATUSES);
            if (deviceSession.isPresent()) {
                return deviceSession;
            }
        }
        User cashier = currentUser(authentication);
        return registerSessionRepository.findFirstByAssignedCashier_IdAndStatusInOrderByOpenedAtDesc(cashier.getId(), CURRENT_STATUSES);
    }

    private RegisterSession save(RegisterSession session) {
        try {
            return registerSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyOpen();
        }
    }

    private RegisterSession saveClosing(RegisterSession session) {
        try {
            return registerSessionRepository.saveAndFlush(session);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Register session was modified by another transaction");
        }
    }

    private void enforceSingleOpenSession(UUID registerId, UUID deviceId) {
        if (properties.isSingleOpenPerRegister()
                && registerSessionRepository.existsByRegister_IdAndStatusIn(registerId, CURRENT_STATUSES)) {
            throw new ConflictException("Register already has an open session");
        }
        if (deviceId != null && properties.isSingleOpenPerDevice()
                && registerSessionRepository.existsByDevice_IdAndStatusIn(deviceId, CURRENT_STATUSES)) {
            throw new ConflictException("Device already has an open register session");
        }
    }

    private Store findStore(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private RegisterSession findSession(UUID id) {
        if (id == null) {
            throw new BadRequestException("id is required");
        }
        return registerSessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
    }

    private Register findRegister(UUID registerId, boolean lock) {
        if (registerId == null) {
            throw new BadRequestException("registerId is required");
        }
        return (lock ? registerRepository.findByIdForUpdate(registerId) : registerRepository.findById(registerId))
                .orElseThrow(() -> new NotFoundException("Register not found"));
    }

    private Device findDevice(UUID deviceId, boolean lock) {
        if (deviceId == null) {
            throw new BadRequestException("deviceId is required");
        }
        return (lock ? deviceRepository.findByIdForUpdate(deviceId) : deviceRepository.findById(deviceId))
                .orElseThrow(() -> new NotFoundException("Device not found"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Authenticated user is required"));
    }

    private static BigDecimal normalizeOpeningCash(BigDecimal openingCash) {
        if (openingCash == null) {
            throw new BadRequestException("openingCash is required");
        }
        if (openingCash.signum() < 0) {
            throw new BadRequestException("openingCash must be greater than or equal to 0.00");
        }
        try {
            return openingCash.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("openingCash may include no more than 2 decimal places");
        }
    }

    private static BigDecimal normalizeCountedCash(BigDecimal countedCash) {
        if (countedCash == null) {
            throw new BadRequestException("countedCash is required");
        }
        if (countedCash.signum() < 0) {
            throw new BadRequestException("countedCash must be greater than or equal to 0.00");
        }
        try {
            return countedCash.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("countedCash may include no more than 2 decimal places");
        }
    }

    private static void validateActive(Store store, Register register) {
        if (!store.isActive()) {
            throw new ConflictException("Store is inactive");
        }
        if (!register.isActive()) {
            throw new ConflictException("Register is inactive");
        }
    }

    private static void validateRelationships(Store store, Register register) {
        if (!register.getStore().getId().equals(store.getId())) {
            throw new BadRequestException("registerId must belong to storeId");
        }
    }

    private static void validateDevice(Store store, Register register, Device device) {
        if (!device.isActive()) {
            throw new ConflictException("Device is inactive");
        }
        if (!device.getStore().getId().equals(store.getId())) {
            throw new BadRequestException("deviceId must belong to storeId");
        }
        if (!device.getRegister().getId().equals(register.getId())) {
            throw new BadRequestException("deviceId must belong to registerId");
        }
    }

    private void validateCashier(User cashier, Register register, Authentication authentication) {
        if (!cashier.isEnabled() || cashier.isLocked()) {
            throw new ForbiddenOperationException("Cashier is not active");
        }
        if (hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_TENANT_OWNER")
                || hasAuthority(authentication, "ROLE_MANAGER") || hasAuthority(authentication, "ROLE_STORE_MANAGER")) {
            return;
        }
        if (!userRegisterAssignmentRepository.existsByUserAndRegister_Id(cashier, register.getId())) {
            throw new ForbiddenOperationException("Cashier is not assigned to this register");
        }
    }

    private static void validateCanClose(RegisterSession session, User closingUser, Authentication authentication) {
        if (hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_TENANT_OWNER")
                || hasAuthority(authentication, "ROLE_MANAGER") || hasAuthority(authentication, "ROLE_STORE_MANAGER")) {
            return;
        }
        if (!session.getAssignedCashier().getId().equals(closingUser.getId())) {
            throw new ForbiddenOperationException("REGISTER_SESSION_OWNED_BY_ANOTHER_USER");
        }
    }

    private boolean canViewCurrent(RegisterSession session, User actor, Authentication authentication) {
        if (storeAccessService != null) {
            storeAccessService.requireStoreAccess(authentication, session.getStore().getId());
        }
        if (isStoreOperator(authentication) && !session.getAssignedCashier().getId().equals(actor.getId())) {
            log.warn("register_event event=REGISTER_SESSION_OPERATION_DENIED tenant_id={} store_id={} register_id={} session_id={} actor_user_id={} operator_user_id={}",
                    actor.getTenantId(), session.getStore().getId(), session.getRegister().getId(), session.getId(), actor.getId(), session.getAssignedCashier().getId());
            return false;
        }
        return true;
    }

    private static boolean isStoreOperator(Authentication authentication) {
        return (hasAuthority(authentication, "ROLE_CASHIER") || hasAuthority(authentication, "ROLE_KITCHEN")) && !isOwner(authentication)
                && !hasAuthority(authentication, "ROLE_MANAGER") && !hasAuthority(authentication, "ROLE_STORE_MANAGER");
    }

    private static boolean isOwner(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_TENANT_OWNER");
    }

    private static boolean isManager(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_MANAGER") || hasAuthority(authentication, "ROLE_STORE_MANAGER");
    }

    private static void requireVersion(RegisterSession session, Long version) {
        if (version == null) {
            throw new BadRequestException("version is required");
        }
        if (session.getVersion() != version) {
            throw new ConflictException("Register session was modified by another transaction");
        }
    }

    private static Specification<RegisterSession> specification(RegisterSessionSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("device", request.deviceId()))
                .and(equalReference("assignedCashier", request.assignedCashierId()))
                .and(equalEnum("status", request.status()))
                .and(openedAtGreaterThanOrEqualTo(request.openedFrom()))
                .and(openedAtLessThanOrEqualTo(request.openedTo()));
    }

    private static Specification<RegisterSession> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<RegisterSession> equalTenant(UUID tenantId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("store").get("tenantId"), tenantId);
    }

    private static Specification<RegisterSession> storeIn(java.util.Set<UUID> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
        }
        return (root, query, criteriaBuilder) -> root.get("store").get("id").in(storeIds);
    }

    private static Specification<RegisterSession> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<RegisterSession> openedAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("openedAt"), value);
    }

    private static Specification<RegisterSession> openedAtLessThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("openedAt"), value);
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    private boolean hasRole(User user, RoleName roleName) {
        return userRoleRepository != null && userRoleRepository.findByUser(user).stream()
                .anyMatch(userRole -> userRole.getRole().getName() == roleName);
    }

    private boolean hasPermission(User user, String permission) {
        return rolePermissionRepository != null
                && rolePermissionRepository.findPermissionCodesByUser(user).contains(permission);
    }

    private static ConflictException alreadyOpen() {
        return new ConflictException("Register session is already open for this register or device");
    }
}
