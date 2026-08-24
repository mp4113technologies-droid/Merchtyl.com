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
import com.merchtyl.sales.PaymentMethod;
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
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Service
public class LotterySaleService {
    private static final int MONEY_SCALE = 2;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String RECORD_ENDPOINT = "POST /api/v1/lottery/sales";
    private static final String CANCEL_ENDPOINT = "POST /api/v1/lottery/sales/{id}/cancel";

    private final LotterySaleRepository lotterySaleRepository;
    private final LotterySaleCancellationRepository lotterySaleCancellationRepository;
    private final LotteryOperatorRepository lotteryOperatorRepository;
    private final LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository;
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
    public LotterySaleService(
            LotterySaleRepository lotterySaleRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository,
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
                lotterySaleRepository,
                lotterySaleCancellationRepository,
                lotteryOperatorRepository,
                lotteryPayoutPolicyRepository,
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

    LotterySaleService(
            LotterySaleRepository lotterySaleRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotteryOperatorRepository lotteryOperatorRepository,
            LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository,
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
        this.lotterySaleRepository = lotterySaleRepository;
        this.lotterySaleCancellationRepository = lotterySaleCancellationRepository;
        this.lotteryOperatorRepository = lotteryOperatorRepository;
        this.lotteryPayoutPolicyRepository = lotteryPayoutPolicyRepository;
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

    public IdempotencyResult recordIdempotently(
            LotterySaleRequest request,
            String idempotencyKey,
            Authentication authentication) {
        User actor = actor(authentication);
        return idempotencyService.execute(actor.getId(), RECORD_ENDPOINT, idempotencyKey, responseBody(request), () -> {
            LotterySaleResponse response = transactions.execute(status -> record(request, actor, authentication));
            return new IdempotencyOperationResponse(
                    201,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    public IdempotencyResult cancelIdempotently(
            UUID saleId,
            LotteryAdjustmentRequest request,
            String idempotencyKey,
            Authentication authentication) {
        User actor = actor(authentication);
        String requestBody = responseBody(new LotterySaleCancellationFingerprint(saleId, request));
        return idempotencyService.execute(actor.getId(), CANCEL_ENDPOINT, idempotencyKey, requestBody, () -> {
            LotterySaleCancellationResponse response = transactions.execute(status -> cancel(saleId, request, actor, authentication));
            return new IdempotencyOperationResponse(
                    200,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    @Transactional
    LotterySaleResponse record(LotterySaleRequest request, User actor, Authentication authentication) {
        if (request == null) {
            throw new BadRequestException("lottery sale request is required");
        }
        Store store = store(request.storeId());
        Register register = register(request.registerId());
        Device device = device(request.deviceId());
        validateRegisterAndDevice(store, register, device);
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, store.getId(), register.getId());

        LotteryOperator operator = operator(request.operatorId());
        if (!operator.isActive()) {
            throw new ConflictException("Lottery operator is inactive");
        }
        if (!store.isActive()) {
            throw new ConflictException("Store is inactive");
        }
        if (!register.isActive()) {
            throw new ConflictException("Register is inactive");
        }
        if (!device.isActive()) {
            throw new ConflictException("Device is inactive");
        }

        BigDecimal amount = normalizeMoney(request.amount(), "amount");
        PaymentMethod paymentMethod = request.paymentMethod();
        if (paymentMethod == null) {
            throw new BadRequestException("paymentMethod is required");
        }
        LotteryGameType gameType = request.gameType();
        if (gameType == null) {
            throw new BadRequestException("gameType is required");
        }
        Instant occurredAt = request.occurredAt() == null ? Instant.now(clock) : request.occurredAt();
        RegisterSession session = resolveSession(request.registerSessionId(), store, register, device, paymentMethod, actor, authentication);

        LotterySale sale = new LotterySale(
                operator,
                cleanOptional(request.operatorReference()),
                cleanOptional(request.ticketReference()),
                gameType,
                amount,
                normalizeCurrencyCode(store.getCurrencyCode()),
                paymentMethod,
                store,
                register,
                device,
                actor,
                session,
                UUID.randomUUID(),
                occurredAt);
        LotterySale saved = save(sale);
        if (paymentMethod == PaymentMethod.CASH) {
            appendCashLedger(saved, actor);
        }
        LotterySaleResponse response = LotterySaleResponse.from(saved);
        audit(actor, response);
        return response;
    }

    @Transactional
    LotterySaleCancellationResponse cancel(UUID saleId, LotteryAdjustmentRequest request, User actor, Authentication authentication) {
        if (request == null) {
            throw new BadRequestException("lottery sale cancellation request is required");
        }
        LotterySale sale = findForUpdate(saleId);
        if (sale.getStatus() != LotterySaleStatus.RECORDED) {
            throw new ConflictException("Only recorded lottery sales can be cancelled");
        }
        if (lotterySaleCancellationRepository.existsByOriginalSale_Id(sale.getId())) {
            throw new ConflictException("Lottery sale has already been cancelled");
        }
        featureService.requireEnabled(FeatureCode.LOTTERY_SALES, sale.getStore().getId(), sale.getRegister().getId());
        requireActiveContext(sale.getStore(), sale.getRegister(), sale.getDevice());
        requireActivePolicy(sale.getOperator(), sale.getStore(), Instant.now(clock));

        RegisterSession session = sale.getRegisterSession();
        boolean cashReturned = sale.getPaymentMethod() == PaymentMethod.CASH;
        if (cashReturned) {
            if (session == null) {
                throw new ConflictException("Register session is required for cash lottery sale cancellation");
            }
            session = registerSessionRepository.findByIdForUpdate(session.getId())
                    .orElseThrow(() -> new NotFoundException("Register session not found"));
            validateSessionRelationships(session, sale.getStore(), sale.getRegister(), sale.getDevice());
            if (session.getStatus() != RegisterSessionStatus.OPEN) {
                throw new ConflictException("Register session is not open");
            }
            validateUserCanUseSession(actor, session, authentication);
        }

        LotterySaleCancellation cancellation = new LotterySaleCancellation(
                sale,
                actor,
                cleanRequired(request.reason(), "reason"),
                cashReturned,
                UUID.randomUUID(),
                Instant.now(clock));
        LotterySaleCancellation saved = saveCancellation(cancellation);
        if (cashReturned) {
            cashLedgerService.append(new CashLedgerEntryCommand(
                    sale.getStore(),
                    sale.getRegister(),
                    session,
                    CashLedgerSourceType.LOTTERY_SALE_CANCELLATION_CASH,
                    saved.getId(),
                    CashLedgerDirection.OUT,
                    sale.getAmount(),
                    sale.getCurrencyCode(),
                    saved.getCancelledAt().atZone(ZoneId.of(sale.getStore().getTimezone())).toLocalDate(),
                    saved.getCancelledAt(),
                    actor,
                    saved.getOperationId(),
                    "Lottery cash sale cancellation"));
        }
        sale.cancel();
        save(sale);
        LotterySaleCancellationResponse response = LotterySaleCancellationResponse.from(saved);
        auditCancellation(actor, saved, response);
        return response;
    }

    @Transactional(readOnly = true)
    public LotterySaleResponse get(UUID id) {
        return LotterySaleResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LotterySaleResponse> search(LotterySaleSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = lotterySaleRepository.findAll(
                Specification.where(equalReference("operator", request.operatorId()))
                        .and(equalReference("store", request.storeId()))
                        .and(equalReference("register", request.registerId()))
                        .and(equalReference("cashier", request.cashierId()))
                        .and(equalReference("registerSession", request.registerSessionId()))
                        .and(equalEnum("gameType", request.gameType()))
                        .and(equalEnum("status", request.status()))
                        .and(equalEnum("paymentMethod", request.paymentMethod()))
                        .and(occurredAtGreaterThanOrEqualTo(request.occurredFrom()))
                        .and(occurredAtLessThanOrEqualTo(request.occurredTo()))
                        .and(historySearch(request.search())),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(LotterySaleResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private RegisterSession resolveSession(
            UUID sessionId,
            Store store,
            Register register,
            Device device,
            PaymentMethod paymentMethod,
            User actor,
            Authentication authentication) {
        if (paymentMethod == PaymentMethod.CASH && sessionId == null) {
            throw new BadRequestException("registerSessionId is required for cash lottery sales");
        }
        if (sessionId == null) {
            return null;
        }
        RegisterSession session = registerSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        validateSessionRelationships(session, store, register, device);
        if (paymentMethod == PaymentMethod.CASH) {
            if (session.getStatus() != RegisterSessionStatus.OPEN) {
                throw new ConflictException("Register session is not open");
            }
            validateUserCanUseSession(actor, session, authentication);
        }
        return session;
    }

    private void appendCashLedger(LotterySale sale, User actor) {
        cashLedgerService.append(new CashLedgerEntryCommand(
                sale.getStore(),
                sale.getRegister(),
                sale.getRegisterSession(),
                CashLedgerSourceType.LOTTERY_SALE_CASH,
                sale.getId(),
                CashLedgerDirection.IN,
                sale.getAmount(),
                sale.getCurrencyCode(),
                sale.getOccurredAt().atZone(ZoneId.of(sale.getStore().getTimezone())).toLocalDate(),
                sale.getOccurredAt(),
                actor,
                sale.getOperationId(),
                "Lottery cash sale"));
    }

    private LotterySale save(LotterySale sale) {
        try {
            return lotterySaleRepository.saveAndFlush(sale);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Lottery sale was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Lottery sale could not be saved");
        }
    }

    private LotterySaleCancellation saveCancellation(LotterySaleCancellation cancellation) {
        try {
            return lotterySaleCancellationRepository.saveAndFlush(cancellation);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Lottery sale cancellation was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Lottery sale has already been cancelled");
        }
    }

    private LotterySale find(UUID id) {
        if (id == null) {
            throw new BadRequestException("lottery sale id is required");
        }
        return lotterySaleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lottery sale not found"));
    }

    private LotterySale findForUpdate(UUID id) {
        if (id == null) {
            throw new BadRequestException("lottery sale id is required");
        }
        return lotterySaleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Lottery sale not found"));
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
            throw new ForbiddenOperationException("Lottery sale user must be assigned to this register session");
        }
    }

    private void requireActivePolicy(LotteryOperator operator, Store store, Instant occurredAt) {
        lotteryPayoutPolicyRepository.findEffectivePolicies(
                        operator.getId(),
                        operator.getJurisdiction().getId(),
                        store.getId(),
                        occurredAt.atZone(ZoneId.of(store.getTimezone())).toLocalDate(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ConflictException("No active lottery payout policy applies to this operator, jurisdiction, store, and business date"));
    }

    private void audit(User actor, LotterySaleResponse response) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.LOTTERY_SALE_RECORDED,
                "LOTTERY_SALE",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                response.paymentMethod().name()));
    }

    private void auditCancellation(User actor, LotterySaleCancellation cancellation, LotterySaleCancellationResponse response) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.LOTTERY_SALE_CANCELLED,
                "LOTTERY_SALE_CANCELLATION",
                response.id(),
                cancellation.getOriginalSale().getStore().getId(),
                cancellation.getOriginalSale().getRegister().getId(),
                null,
                response,
                response.reason()));
    }

    private String responseBody(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Response must be JSON serializable", exception);
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

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
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

    private static Specification<LotterySale> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<LotterySale> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<LotterySale> occurredAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), value);
    }

    private static Specification<LotterySale> occurredAtLessThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), value);
    }

    private static Specification<LotterySale> historySearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("operatorReference")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("ticketReference")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("operator").get("code")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("operator").get("name")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("register").get("code")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("register").get("name")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("cashier").get("email")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("cashier").get("displayName")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("operationId").as(String.class)), pattern));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private record LotterySaleCancellationFingerprint(UUID saleId, LotteryAdjustmentRequest request) {
    }
}
