package com.merchtyl.lottery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerEntryCommand;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashLedgerSourceType;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.idempotency.IdempotencyOperationResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LotteryPayoutService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MONEY_SCALE = 2;
    private static final String COMPLETE_CASH_ENDPOINT = "POST /api/v1/lottery/payouts/{id}/complete-cash";
    private static final String REVERSE_ENDPOINT = "POST /api/v1/lottery/payouts/{id}/reverse";

    private final LotteryPayoutRepository lotteryPayoutRepository;
    private final LotteryPayoutReversalRepository lotteryPayoutReversalRepository;
    private final LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository;
    private final LotteryOperatorRepository lotteryOperatorRepository;
    private final StoreRepository storeRepository;
    private final RegisterRepository registerRepository;
    private final DeviceRepository deviceRepository;
    private final RegisterSessionRepository registerSessionRepository;
    private final UserRepository userRepository;
    private final FeatureService featureService;
    private final CashLedgerService cashLedgerService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public LotteryPayoutService(
            LotteryPayoutRepository lotteryPayoutRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            DeviceRepository deviceRepository,
            RegisterSessionRepository registerSessionRepository,
            UserRepository userRepository,
            FeatureService featureService,
            CashLedgerService cashLedgerService,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            TransactionOperations idempotencyTransactionOperations) {
        this(
                lotteryPayoutRepository,
                lotteryPayoutReversalRepository,
                lotteryPayoutPolicyRepository,
                lotteryOperatorRepository,
                storeRepository,
                registerRepository,
                deviceRepository,
                registerSessionRepository,
                userRepository,
                featureService,
                cashLedgerService,
                auditService,
                idempotencyService,
                objectMapper,
                idempotencyTransactionOperations,
                Clock.systemUTC());
    }

    LotteryPayoutService(
            LotteryPayoutRepository lotteryPayoutRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            DeviceRepository deviceRepository,
            RegisterSessionRepository registerSessionRepository,
            UserRepository userRepository,
            FeatureService featureService,
            CashLedgerService cashLedgerService,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            TransactionOperations transactions,
            Clock clock) {
        this.lotteryPayoutRepository = lotteryPayoutRepository;
        this.lotteryPayoutReversalRepository = lotteryPayoutReversalRepository;
        this.lotteryPayoutPolicyRepository = lotteryPayoutPolicyRepository;
        this.lotteryOperatorRepository = lotteryOperatorRepository;
        this.storeRepository = storeRepository;
        this.registerRepository = registerRepository;
        this.deviceRepository = deviceRepository;
        this.registerSessionRepository = registerSessionRepository;
        this.userRepository = userRepository;
        this.featureService = featureService;
        this.cashLedgerService = cashLedgerService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public LotteryPayoutResponse create(LotteryPayoutCreateRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Store store = store(request.storeId());
        Register register = register(request.registerId());
        Device device = device(request.deviceId());
        validateRegisterAndDevice(store, register, device);
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, store.getId(), register.getId());

        LotteryOperator operator = operator(request.operatorId());
        if (!operator.isActive()) {
            throw new ConflictException("Lottery operator is inactive");
        }
        requireActiveContext(store, register, device);
        Instant occurredAt = request.occurredAt() == null ? Instant.now(clock) : request.occurredAt();
        LocalDate businessDate = request.businessDate() == null
                ? occurredAt.atZone(ZoneId.of(store.getTimezone())).toLocalDate()
                : request.businessDate();
        LotteryPayoutPolicy policy = effectivePolicy(operator, store, businessDate);
        LotteryPayoutMethod method = requireNonNull(request.payoutMethod(), "payoutMethod");
        validateMethodAllowed(policy, method);
        RegisterSession session = resolveSession(request.registerSessionId(), store, register, device, method, actor, authentication);

        LotteryPayout payout = new LotteryPayout(
                operator,
                policy,
                store,
                register,
                device,
                actor,
                session,
                cleanRequired(request.ticketNumber(), "ticketNumber"),
                normalizeMoney(request.amount(), "amount"),
                normalizeCurrencyCode(store.getCurrencyCode()),
                method,
                businessDate,
                occurredAt,
                cleanOptional(request.notes()));
        LotteryPayoutResponse response = LotteryPayoutResponse.from(save(payout));
        audit(actor, AuditAction.LOTTERY_PAYOUT_CREATED, response, null);
        return response;
    }

    @Transactional
    public LotteryPayoutResponse validate(UUID id, LotteryPayoutValidationRequest request, Authentication authentication) {
        User actor = actor(authentication);
        LotteryPayout payout = findForUpdate(id);
        requireVersion(payout, request.version());
        if (payout.getStatus() != LotteryPayoutStatus.DRAFT) {
            throw new ConflictException("Only draft lottery payouts can be validated");
        }

        LotteryVerificationState ticket = verificationState(
                request.ticketValidationState(),
                payout.isTicketValidationRequired(),
                "ticketValidationState");
        LotteryVerificationState age = verificationState(
                request.ageVerificationState(),
                payout.isAgeVerificationRequired(),
                "ageVerificationState");
        LotteryVerificationState identification = verificationState(
                request.identificationVerificationState(),
                payout.isIdentificationRequired(),
                "identificationVerificationState");
        if (ticket == LotteryVerificationState.FAILED || age == LotteryVerificationState.FAILED || identification == LotteryVerificationState.FAILED) {
            payout.reject(actor, Instant.now(clock), "Lottery payout verification failed");
            LotteryPayoutResponse response = LotteryPayoutResponse.from(save(payout));
            audit(actor, AuditAction.LOTTERY_PAYOUT_REJECTED, response, response.rejectionReason());
            return response;
        }
        requireVerified(ticket, payout.isTicketValidationRequired(), "ticket validation");
        requireVerified(age, payout.isAgeVerificationRequired(), "age verification");
        requireVerified(identification, payout.isIdentificationRequired(), "identification verification");

        LotteryPayoutStatus resultingStatus = referredToOperator(payout)
                ? LotteryPayoutStatus.REFERRED_TO_OPERATOR
                : LotteryPayoutStatus.VALIDATED;
        payout.validate(
                ticket,
                age,
                identification,
                cleanOptional(request.validationReference()),
                actor,
                Instant.now(clock),
                resultingStatus);
        if (resultingStatus == LotteryPayoutStatus.REFERRED_TO_OPERATOR) {
            payout.addReferralApproval(actor, Instant.now(clock), "Payout exceeds operator referral threshold or cash payout maximum");
        }
        LotteryPayoutResponse response = LotteryPayoutResponse.from(save(payout));
        audit(actor, AuditAction.LOTTERY_PAYOUT_VALIDATED, response, resultingStatus.name());
        return response;
    }

    @Transactional
    public LotteryPayoutResponse authorize(UUID id, LotteryPayoutAuthorizationRequest request, Authentication authentication) {
        User actor = actor(authentication);
        LotteryPayout payout = findForUpdate(id);
        requireVersion(payout, request.version());
        if (payout.getStatus() == LotteryPayoutStatus.REFERRED_TO_OPERATOR) {
            throw new ConflictException("Lottery payout has been referred to the operator");
        }
        if (payout.getStatus() != LotteryPayoutStatus.VALIDATED) {
            throw new ConflictException("Only validated lottery payouts can be authorized");
        }

        LotteryPayoutApprovalType approvalType;
        BigDecimal threshold;
        if (payout.getAmount().compareTo(payout.getCashierApprovalLimit()) <= 0) {
            approvalType = LotteryPayoutApprovalType.CASHIER_LIMIT;
            threshold = payout.getCashierApprovalLimit();
        } else {
            if (!hasAuthority(authentication, PermissionCode.LOTTERY_PAYOUT_APPROVE.name())) {
                throw new ForbiddenOperationException("Lottery payout requires manager approval");
            }
            approvalType = LotteryPayoutApprovalType.MANAGER_APPROVAL;
            threshold = payout.getManagerApprovalThreshold();
        }
        payout.authorize(actor, Instant.now(clock), approvalType, threshold, cleanOptional(request.approvalNotes()));
        LotteryPayoutResponse response = LotteryPayoutResponse.from(save(payout));
        audit(actor, AuditAction.LOTTERY_PAYOUT_AUTHORIZED, response, approvalType.name());
        return response;
    }

    @Transactional
    public LotteryPayoutResponse reject(UUID id, LotteryPayoutRejectRequest request, Authentication authentication) {
        User actor = actor(authentication);
        LotteryPayout payout = findForUpdate(id);
        requireVersion(payout, request.version());
        if (payout.getStatus() == LotteryPayoutStatus.PAID || payout.getStatus() == LotteryPayoutStatus.REVERSED) {
            throw new ConflictException("Paid or reversed lottery payouts cannot be rejected");
        }
        payout.reject(actor, Instant.now(clock), cleanRequired(request.reason(), "reason"));
        LotteryPayoutResponse response = LotteryPayoutResponse.from(save(payout));
        audit(actor, AuditAction.LOTTERY_PAYOUT_REJECTED, response, response.rejectionReason());
        return response;
    }

    public IdempotencyResult completeCashIdempotently(UUID id, String idempotencyKey, Authentication authentication) {
        User actor = actor(authentication);
        String requestBody = "{\"payoutId\":\"" + id + "\"}";
        return idempotencyService.execute(actor.getId(), COMPLETE_CASH_ENDPOINT, idempotencyKey, requestBody, () -> {
            LotteryPayoutResponse response = transactions.execute(status -> completeCash(id, actor, authentication));
            return new IdempotencyOperationResponse(
                    200,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    public IdempotencyResult reverseIdempotently(
            UUID id,
            LotteryAdjustmentRequest request,
            String idempotencyKey,
            Authentication authentication) {
        User actor = actor(authentication);
        String requestBody = responseBody(new LotteryPayoutReversalFingerprint(id, request));
        return idempotencyService.execute(actor.getId(), REVERSE_ENDPOINT, idempotencyKey, requestBody, () -> {
            LotteryPayoutReversalResponse response = transactions.execute(status -> reverse(id, request, actor, authentication));
            return new IdempotencyOperationResponse(
                    200,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    @Transactional
    LotteryPayoutResponse completeCash(UUID id, User actor, Authentication authentication) {
        LotteryPayout payout = findForUpdate(id);
        if (payout.getPayoutMethod() != LotteryPayoutMethod.CASH) {
            throw new ConflictException("Only cash lottery payouts can be completed as cash");
        }
        if (payout.getStatus() == LotteryPayoutStatus.REFERRED_TO_OPERATOR) {
            throw new ConflictException("Lottery payout has been referred to the operator");
        }
        if (payout.getStatus() != LotteryPayoutStatus.AUTHORIZED) {
            throw new ConflictException("Only authorized lottery payouts can be completed");
        }
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, payout.getStore().getId(), payout.getRegister().getId());
        requireActiveContext(payout.getStore(), payout.getRegister(), payout.getDevice());
        requireValidationComplete(payout);
        requireApprovalComplete(payout);

        LotteryPayoutPolicy activePolicy = effectivePolicy(payout.getOperator(), payout.getStore(), payout.getBusinessDate());
        if (!activePolicy.getId().equals(payout.getPolicy().getId())) {
            throw new ConflictException("Lottery payout policy is no longer active for this payout");
        }
        validateMethodAllowed(activePolicy, LotteryPayoutMethod.CASH);
        if (payout.getAmount().compareTo(activePolicy.getOperatorReferralThreshold()) >= 0
                || payout.getAmount().compareTo(activePolicy.getMaximumCashPayout()) > 0) {
            throw new ConflictException("Lottery payout must be referred to the operator");
        }

        if (payout.getRegisterSession() == null) {
            throw new ConflictException("Register session is required for cash lottery payouts");
        }
        RegisterSession session = registerSessionRepository.findByIdForUpdate(payout.getRegisterSession().getId())
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        validateSessionRelationships(session, payout.getStore(), payout.getRegister(), payout.getDevice());
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
        if (session.getBusinessDay() != null && !session.isBusinessDayOperational()) {
            throw new ConflictException("BUSINESS_DAY_NOT_OPEN");
        }
        validateUserCanUseSession(actor, session, authentication);

        LotteryPayoutCashAvailabilityResponse availability = availability(session, activePolicy, payout.getId());
        if (availability.availablePayoutCash().compareTo(payout.getAmount()) < 0) {
            throw new ConflictException("Insufficient available cash for lottery payout");
        }

        Instant paidAt = Instant.now(clock);
        cashLedgerService.append(new CashLedgerEntryCommand(
                payout.getStore(),
                payout.getRegister(),
                session,
                CashLedgerSourceType.LOTTERY_PAYOUT_CASH,
                payout.getId(),
                CashLedgerDirection.OUT,
                payout.getAmount(),
                payout.getCurrencyCode(),
                payout.getBusinessDate(),
                paidAt,
                actor,
                payout.getId(),
                "Lottery cash payout"));
        payout.completeCash(actor, paidAt);
        LotteryPayoutResponse response = LotteryPayoutResponse.from(save(payout));
        audit(actor, AuditAction.LOTTERY_PAYOUT_PAID, response, "CASH");
        return response;
    }

    @Transactional
    LotteryPayoutReversalResponse reverse(UUID id, LotteryAdjustmentRequest request, User actor, Authentication authentication) {
        if (request == null) {
            throw new BadRequestException("lottery payout reversal request is required");
        }
        LotteryPayout payout = findForUpdate(id);
        if (payout.getStatus() != LotteryPayoutStatus.PAID) {
            throw new ConflictException("Only paid lottery payouts can be reversed");
        }
        if (lotteryPayoutReversalRepository.existsByOriginalPayout_Id(payout.getId())) {
            throw new ConflictException("Lottery payout has already been reversed");
        }
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, payout.getStore().getId(), payout.getRegister().getId());
        requireActiveContext(payout.getStore(), payout.getRegister(), payout.getDevice());
        if (payout.getRegisterSession() == null) {
            throw new ConflictException("Register session is required for lottery payout reversal");
        }
        RegisterSession session = registerSessionRepository.findByIdForUpdate(payout.getRegisterSession().getId())
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        validateSessionRelationships(session, payout.getStore(), payout.getRegister(), payout.getDevice());
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
        if (session.getBusinessDay() != null && !session.isBusinessDayOperational()) {
            throw new ConflictException("BUSINESS_DAY_NOT_OPEN");
        }
        validateUserCanUseSession(actor, session, authentication);

        Instant reversedAt = Instant.now(clock);
        LotteryPayoutReversal reversal = new LotteryPayoutReversal(
                payout,
                actor,
                cleanRequired(request.reason(), "reason"),
                UUID.randomUUID(),
                reversedAt);
        LotteryPayoutReversal saved = saveReversal(reversal);
        cashLedgerService.append(new CashLedgerEntryCommand(
                payout.getStore(),
                payout.getRegister(),
                session,
                CashLedgerSourceType.LOTTERY_PAYOUT_REVERSAL,
                saved.getId(),
                CashLedgerDirection.IN,
                payout.getAmount(),
                payout.getCurrencyCode(),
                reversedAt.atZone(ZoneId.of(payout.getStore().getTimezone())).toLocalDate(),
                reversedAt,
                actor,
                saved.getOperationId(),
                "Lottery payout reversal"));
        payout.reverse();
        save(payout);
        LotteryPayoutReversalResponse response = LotteryPayoutReversalResponse.from(saved);
        auditReversal(actor, saved, response);
        return response;
    }

    @Transactional(readOnly = true)
    public LotteryPayoutCashAvailabilityResponse availableCash(UUID registerSessionId, UUID operatorId) {
        RegisterSession session = registerSessionRepository.findById(registerSessionId)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
        if (session.getBusinessDay() != null && !session.isBusinessDayOperational()) {
            throw new ConflictException("BUSINESS_DAY_NOT_OPEN");
        }
        requireActiveContext(session.getStore(), session.getRegister(), session.getDevice());
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, session.getStore().getId(), session.getRegister().getId());
        LotteryOperator operator = operator(operatorId);
        if (!operator.isActive()) {
            throw new ConflictException("Lottery operator is inactive");
        }
        LocalDate businessDate = Instant.now(clock).atZone(ZoneId.of(session.getStore().getTimezone())).toLocalDate();
        LotteryPayoutPolicy policy = effectivePolicy(operator, session.getStore(), businessDate);
        return availability(session, policy, null);
    }

    @Transactional(readOnly = true)
    public LotteryPayoutResponse get(UUID id) {
        return LotteryPayoutResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LotteryPayoutResponse> search(LotteryPayoutSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = lotteryPayoutRepository.findAll(
                Specification.where(equalReference("operator", request.operatorId()))
                        .and(equalReference("store", request.storeId()))
                        .and(equalReference("register", request.registerId()))
                        .and(equalReference("registerSession", request.registerSessionId()))
                        .and(equalEnum("status", request.status())),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(LotteryPayoutResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private LotteryPayoutPolicy effectivePolicy(LotteryOperator operator, Store store, LocalDate businessDate) {
        return lotteryPayoutPolicyRepository.findEffectivePolicies(
                        operator.getId(),
                        operator.getJurisdiction().getId(),
                        store.getId(),
                        businessDate,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ConflictException("No active lottery payout policy applies to this operator, jurisdiction, store, and business date"));
    }

    private LotteryPayoutCashAvailabilityResponse availability(
            RegisterSession session,
            LotteryPayoutPolicy policy,
            UUID excludePayoutId) {
        BigDecimal expectedDrawerCash = cashLedgerService.expectedCash(session).setScale(MONEY_SCALE);
        BigDecimal protectedRegisterFloat = policy.getProtectedRegisterFloat().setScale(MONEY_SCALE);
        BigDecimal reservedObligations = lotteryPayoutRepository.sumReservedCashObligations(session.getId(), excludePayoutId);
        if (reservedObligations == null) {
            reservedObligations = BigDecimal.ZERO;
        }
        reservedObligations = reservedObligations.setScale(MONEY_SCALE);
        BigDecimal availablePayoutCash = expectedDrawerCash
                .subtract(protectedRegisterFloat)
                .subtract(reservedObligations)
                .setScale(MONEY_SCALE);
        return new LotteryPayoutCashAvailabilityResponse(
                session.getId(),
                policy.getId(),
                expectedDrawerCash,
                protectedRegisterFloat,
                reservedObligations,
                availablePayoutCash,
                session.getStore().getCurrencyCode());
    }

    private RegisterSession resolveSession(
            UUID sessionId,
            Store store,
            Register register,
            Device device,
            LotteryPayoutMethod method,
            User actor,
            Authentication authentication) {
        if (method == LotteryPayoutMethod.CASH && sessionId == null) {
            throw new BadRequestException("registerSessionId is required for cash lottery payouts");
        }
        if (sessionId == null) {
            return null;
        }
        RegisterSession session = registerSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        validateSessionRelationships(session, store, register, device);
        if (method == LotteryPayoutMethod.CASH) {
            if (session.getStatus() != RegisterSessionStatus.OPEN) {
                throw new ConflictException("Register session is not open");
            }
            if (session.getBusinessDay() != null && !session.isBusinessDayOperational()) {
                throw new ConflictException("BUSINESS_DAY_NOT_OPEN");
            }
            validateUserCanUseSession(actor, session, authentication);
        }
        return session;
    }

    private static void validateMethodAllowed(LotteryPayoutPolicy policy, LotteryPayoutMethod method) {
        if (method == LotteryPayoutMethod.CASH && !policy.isAllowCashPayout()) {
            throw new ConflictException("Cash lottery payouts are disabled by policy");
        }
        if (method == LotteryPayoutMethod.STORE_CREDIT && !policy.isAllowStoreCredit()) {
            throw new ConflictException("Store-credit lottery payouts are disabled by policy");
        }
    }

    private static boolean referredToOperator(LotteryPayout payout) {
        return payout.getAmount().compareTo(payout.getOperatorReferralThreshold()) >= 0
                || (payout.getPayoutMethod() == LotteryPayoutMethod.CASH
                && payout.getAmount().compareTo(payout.getMaximumCashPayout()) > 0)
                || payout.getPayoutMethod() == LotteryPayoutMethod.OPERATOR_CLAIM_REFERRAL
                || payout.getPayoutMethod() == LotteryPayoutMethod.CHEQUE_REFERRAL;
    }

    private LotteryPayout find(UUID id) {
        if (id == null) {
            throw new BadRequestException("lottery payout id is required");
        }
        return lotteryPayoutRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery payout not found"));
    }

    private LotteryPayout findForUpdate(UUID id) {
        if (id == null) {
            throw new BadRequestException("lottery payout id is required");
        }
        return lotteryPayoutRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Lottery payout not found"));
    }

    private LotteryPayout save(LotteryPayout payout) {
        try {
            return lotteryPayoutRepository.saveAndFlush(payout);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Lottery payout was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Lottery payout could not be saved");
        }
    }

    private LotteryPayoutReversal saveReversal(LotteryPayoutReversal reversal) {
        try {
            return lotteryPayoutReversalRepository.saveAndFlush(reversal);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Lottery payout reversal was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Lottery payout has already been reversed");
        }
    }

    private LotteryOperator operator(UUID id) {
        if (id == null) {
            throw new BadRequestException("operatorId is required");
        }
        return lotteryOperatorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery operator not found"));
    }

    private Store store(UUID id) {
        if (id == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Register register(UUID id) {
        if (id == null) {
            throw new BadRequestException("registerId is required");
        }
        return registerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Register not found"));
    }

    private Device device(UUID id) {
        if (id == null) {
            throw new BadRequestException("deviceId is required");
        }
        return deviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Device not found"));
    }

    private User actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Authenticated user is required");
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Authenticated user is required"));
        if (!user.isEnabled() || user.isLocked()) {
            throw new ForbiddenOperationException("User is not active");
        }
        return user;
    }

    private static void requireVersion(LotteryPayout payout, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != payout.getVersion()) {
            throw new ConflictException("Lottery payout was modified by another transaction");
        }
    }

    private static void requireActiveContext(Store store, Register register, Device device) {
        if (!store.isActive()) {
            throw new ConflictException("Store is inactive");
        }
        if (!register.isActive()) {
            throw new ConflictException("Register is inactive");
        }
        if (!device.isActive()) {
            throw new ConflictException("Device is inactive");
        }
    }

    private static void validateRegisterAndDevice(Store store, Register register, Device device) {
        if (!register.getStore().getId().equals(store.getId())) {
            throw new BadRequestException("register must belong to store");
        }
        if (!device.getStore().getId().equals(store.getId())) {
            throw new BadRequestException("device must belong to store");
        }
        if (!device.getRegister().getId().equals(register.getId())) {
            throw new BadRequestException("device must belong to register");
        }
    }

    private static void validateSessionRelationships(RegisterSession session, Store store, Register register, Device device) {
        if (!session.getStore().getId().equals(store.getId())) {
            throw new BadRequestException("registerSession must belong to store");
        }
        if (!session.getRegister().getId().equals(register.getId())) {
            throw new BadRequestException("registerSession must belong to register");
        }
        if (session.getDevice() != null && !session.getDevice().getId().equals(device.getId())) {
            throw new BadRequestException("registerSession must belong to device");
        }
    }

    private static void validateUserCanUseSession(User actor, RegisterSession session, Authentication authentication) {
        if (hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_MANAGER")) {
            return;
        }
        if (!session.getAssignedCashier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Lottery payout user must be assigned to this register session");
        }
    }

    private static LotteryVerificationState verificationState(
            LotteryVerificationState requested,
            boolean required,
            String field) {
        if (!required) {
            return LotteryVerificationState.NOT_REQUIRED;
        }
        if (requested == null) {
            throw new BadRequestException(field + " is required");
        }
        if (requested == LotteryVerificationState.NOT_REQUIRED) {
            throw new BadRequestException(field + " cannot be NOT_REQUIRED");
        }
        return requested;
    }

    private static void requireVerified(LotteryVerificationState state, boolean required, String label) {
        if (required && state != LotteryVerificationState.VERIFIED) {
            throw new ConflictException(label + " must be verified");
        }
    }

    private static void requireValidationComplete(LotteryPayout payout) {
        requireVerified(payout.getTicketValidationState(), payout.isTicketValidationRequired(), "ticket validation");
        requireVerified(payout.getAgeVerificationState(), payout.isAgeVerificationRequired(), "age verification");
        requireVerified(payout.getIdentificationVerificationState(), payout.isIdentificationRequired(), "identification verification");
    }

    private static void requireApprovalComplete(LotteryPayout payout) {
        LotteryPayoutApprovalType requiredApproval = payout.getAmount().compareTo(payout.getCashierApprovalLimit()) <= 0
                ? LotteryPayoutApprovalType.CASHIER_LIMIT
                : LotteryPayoutApprovalType.MANAGER_APPROVAL;
        boolean approved = payout.getApprovals().stream()
                .anyMatch(approval -> approval.getApprovalType() == requiredApproval);
        if (!approved) {
            throw new ConflictException("Lottery payout approval is incomplete");
        }
    }

    private void audit(User actor, AuditAction action, LotteryPayoutResponse response, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                action,
                "LOTTERY_PAYOUT",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                reason));
    }

    private void auditReversal(User actor, LotteryPayoutReversal reversal, LotteryPayoutReversalResponse response) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.LOTTERY_PAYOUT_REVERSED,
                "LOTTERY_PAYOUT_REVERSAL",
                response.id(),
                reversal.getOriginalPayout().getStore().getId(),
                reversal.getOriginalPayout().getRegister().getId(),
                null,
                response,
                response.reason()));
    }

    private String responseBody(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize lottery payout response", exception);
        }
    }

    private static BigDecimal normalizeMoney(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new BadRequestException(fieldName + " must be greater than zero");
        }
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(fieldName + " may include no more than 2 decimal places");
        }
    }

    private static String normalizeCurrencyCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("currencyCode is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private static Specification<LotteryPayout> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotteryPayout> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private record LotteryPayoutReversalFingerprint(UUID payoutId, LotteryAdjustmentRequest request) {
    }
}
