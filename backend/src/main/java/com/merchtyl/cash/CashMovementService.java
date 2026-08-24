package com.merchtyl.cash;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class CashMovementService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MONEY_SCALE = 2;

    private final CashMovementRepository cashMovementRepository;
    private final RegisterSessionRepository registerSessionRepository;
    private final UserRepository userRepository;
    private final CashLedgerService cashLedgerService;
    private final AuditService auditService;
    private final CashMovementProperties properties;
    private final Clock clock;

    @Autowired
    public CashMovementService(
            CashMovementRepository cashMovementRepository,
            RegisterSessionRepository registerSessionRepository,
            UserRepository userRepository,
            CashLedgerService cashLedgerService,
            AuditService auditService,
            CashMovementProperties properties) {
        this(cashMovementRepository, registerSessionRepository, userRepository, cashLedgerService, auditService, properties, Clock.systemUTC());
    }

    CashMovementService(
            CashMovementRepository cashMovementRepository,
            RegisterSessionRepository registerSessionRepository,
            UserRepository userRepository,
            CashLedgerService cashLedgerService,
            AuditService auditService,
            CashMovementProperties properties,
            Clock clock) {
        this.cashMovementRepository = cashMovementRepository;
        this.registerSessionRepository = registerSessionRepository;
        this.userRepository = userRepository;
        this.cashLedgerService = cashLedgerService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CashMovementResponse create(CashMovementRequest request, Authentication authentication) {
        User actor = actor(authentication);
        RegisterSession session = findOpenSession(request.registerSessionId());
        validateUserCanUseSession(actor, session, authentication);

        CashMovementType type = requireNonNull(request.type(), "type");
        CashLedgerDirection direction = direction(type, request.direction());
        BigDecimal amount = normalizeAmount(request.amount());
        String reason = cleanRequired(request.reason(), "reason");
        Instant occurredAt = requireNonNull(request.occurredAt(), "occurredAt");
        User approvedBy = null;
        Instant approvedAt = null;
        String approvalNotes = cleanOptional(request.approvalNotes());
        if (properties.requiresApproval(type)) {
            if (!hasAuthority(authentication, PermissionCode.CASH_MOVEMENT_APPROVE.name())) {
                throw new ForbiddenOperationException(type.name() + " requires cash movement approval");
            }
            approvedBy = actor;
            approvedAt = Instant.now(clock);
        }

        CashMovement movement = new CashMovement(
                session.getStore(),
                session.getRegister(),
                session,
                type,
                direction,
                amount,
                session.getStore().getCurrencyCode(),
                reason,
                cleanOptional(request.notes()),
                actor,
                occurredAt,
                approvedBy,
                approvedAt,
                approvalNotes);
        CashMovement saved = save(movement);
        cashLedgerService.append(new CashLedgerEntryCommand(
                saved.getStore(),
                saved.getRegister(),
                saved.getRegisterSession(),
                CashLedgerSourceType.CASH_MOVEMENT,
                saved.getId(),
                saved.getDirection(),
                saved.getAmount(),
                saved.getCurrencyCode(),
                businessDate(saved),
                saved.getOccurredAt(),
                actor,
                saved.getId(),
                saved.getReason()));
        CashMovementResponse response = CashMovementResponse.from(saved);
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.CASH_MOVEMENT_CREATED,
                "CASH_MOVEMENT",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                response.reason()));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CashMovementResponse> search(CashMovementSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = cashMovementRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(CashMovementResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private CashMovement save(CashMovement movement) {
        try {
            return cashMovementRepository.saveAndFlush(movement);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Cash movement was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Cash movement could not be recorded");
        }
    }

    private RegisterSession findOpenSession(UUID registerSessionId) {
        if (registerSessionId == null) {
            throw new BadRequestException("registerSessionId is required");
        }
        RegisterSession session = registerSessionRepository.findById(registerSessionId)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
        return session;
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

    private static void validateUserCanUseSession(User actor, RegisterSession session, Authentication authentication) {
        if (hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_TENANT_OWNER")
                || hasAuthority(authentication, "ROLE_MANAGER") || hasAuthority(authentication, "ROLE_STORE_MANAGER")) {
            return;
        }
        if (!session.getAssignedCashier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Cash movement user must be assigned to this register session");
        }
    }

    private static CashLedgerDirection direction(CashMovementType type, CashLedgerDirection requested) {
        return switch (type) {
            case CASH_IN, FLOAT_ADD -> CashLedgerDirection.IN;
            case CASH_OUT, SAFE_DROP, FLOAT_REMOVE, EXPENSE, BANK_DEPOSIT -> CashLedgerDirection.OUT;
            case CORRECTION -> requireNonNull(requested, "direction");
        };
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("amount is required");
        }
        if (amount.signum() <= 0) {
            throw new BadRequestException("amount must be greater than 0.00");
        }
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("amount may include no more than 2 decimal places");
        }
    }

    private static LocalDate businessDate(CashMovement movement) {
        return movement.getOccurredAt()
                .atZone(ZoneId.of(movement.getStore().getTimezone()))
                .toLocalDate();
    }

    private static Specification<CashMovement> specification(CashMovementSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("registerSession", request.registerSessionId()))
                .and(equalEnum("type", request.type()))
                .and(occurredAtGreaterThanOrEqualTo(request.occurredFrom()))
                .and(occurredAtLessThanOrEqualTo(request.occurredTo()));
    }

    private static Specification<CashMovement> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<CashMovement> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<CashMovement> occurredAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), value);
    }

    private static Specification<CashMovement> occurredAtLessThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), value);
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

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }
}
