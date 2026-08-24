package com.merchtyl.refunds;

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
import com.merchtyl.idempotency.IdempotencyOperationResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.inventory.InventoryService;
import com.merchtyl.inventory.InventoryStockChangeRequest;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.returns.Return;
import com.merchtyl.returns.ReturnItem;
import com.merchtyl.returns.ReturnRepository;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RefundService {
    private static final int MONEY_SCALE = 2;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CREATE_ENDPOINT = "POST /api/v1/refunds";
    private static final String REFUND_REFERENCE_TYPE = "REFUND";

    private final RefundRepository refundRepository;
    private final RefundPaymentRepository refundPaymentRepository;
    private final ReturnRepository returnRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final CashLedgerService cashLedgerService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final RefundProperties properties;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public RefundService(
            RefundRepository refundRepository,
            RefundPaymentRepository refundPaymentRepository,
            ReturnRepository returnRepository,
            SaleRepository saleRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            CashLedgerService cashLedgerService,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            RefundProperties properties,
            TransactionOperations idempotencyTransactionOperations) {
        this(
                refundRepository,
                refundPaymentRepository,
                returnRepository,
                saleRepository,
                userRepository,
                inventoryService,
                cashLedgerService,
                auditService,
                idempotencyService,
                objectMapper,
                properties,
                idempotencyTransactionOperations,
                Clock.systemUTC());
    }

    RefundService(
            RefundRepository refundRepository,
            RefundPaymentRepository refundPaymentRepository,
            ReturnRepository returnRepository,
            SaleRepository saleRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            CashLedgerService cashLedgerService,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            RefundProperties properties,
            TransactionOperations transactions,
            Clock clock) {
        this.refundRepository = refundRepository;
        this.refundPaymentRepository = refundPaymentRepository;
        this.returnRepository = returnRepository;
        this.saleRepository = saleRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.cashLedgerService = cashLedgerService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    public IdempotencyResult createIdempotently(RefundCreateRequest request, String idempotencyKey, Authentication authentication) {
        User actor = actor(authentication);
        String requestBody = requestBody(request);
        return idempotencyService.execute(actor.getId(), CREATE_ENDPOINT, idempotencyKey, requestBody, () -> {
            RefundResponse response = transactions.execute(status -> create(request, actor, authentication));
            return new IdempotencyOperationResponse(
                    201,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    @Transactional
    RefundResponse create(RefundCreateRequest request, User actor, Authentication authentication) {
        Return returnRecord = returnRepository.findByIdForUpdate(required(request.returnId(), "returnId"))
                .orElseThrow(() -> new NotFoundException("Return not found"));
        if (refundRepository.existsByReturnRecord_Id(returnRecord.getId())) {
            throw new ConflictException("Return has already been refunded");
        }
        Sale sale = saleRepository.findByIdForUpdate(returnRecord.getOriginalSale().getId())
                .orElseThrow(() -> new NotFoundException("Original sale not found"));
        requireRefundableSale(sale);
        requireOpenRegisterSession(sale);
        validateUserCanUseSession(actor, sale, authentication);

        String reason = cleanRequired(request.reason(), "reason");
        User approvedBy = null;
        Instant approvedAt = null;
        String approvalNotes = cleanOptional(request.approvalNotes());
        if (properties.isApprovalRequired()) {
            if (!hasAuthority(authentication, PermissionCode.REFUND_APPROVE.name())) {
                throw new ForbiddenOperationException("Refund requires approval");
            }
            approvedBy = actor;
            approvedAt = Instant.now(clock);
        }

        Refund refund = new Refund(returnRecord, actor, Instant.now(clock), reason, approvedBy, approvedAt, approvalNotes);
        addPayments(refund, request.payments(), sale);
        requirePaymentsMatchRefundTotal(refund);
        addTaxSnapshots(refund, returnRecord);
        Refund saved = save(refund);

        restoreInventory(saved, authentication);
        appendCashLedger(saved, actor);
        markSaleRefunded(sale);
        Sale savedSale = saveSale(sale);

        RefundResponse response = RefundResponse.from(saved);
        audit(actor, response, savedSale.getStatus());
        return response;
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID id) {
        Refund refund = refundRepository.findById(required(id, "refund id"))
                .orElseThrow(() -> new NotFoundException("Refund not found"));
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> search(RefundSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = refundRepository.findAll(
                Specification.where(equalUuid("originalSale", "id", request.originalSaleId()))
                        .and(equalUuid("returnRecord", "id", request.returnId()))
                        .and(equalUuid("store", "id", request.storeId()))
                        .and(equalUuid("registerSession", "id", request.registerSessionId())),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(RefundResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private void addPayments(Refund refund, List<RefundPaymentRequest> requests, Sale sale) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("payments are required");
        }
        Set<UUID> referencedPayments = new HashSet<>();
        for (RefundPaymentRequest request : requests) {
            PaymentMethod method = requireNonNull(request.method(), "method");
            BigDecimal amount = normalizeAmount(request.amount(), "amount");
            String reference = cleanOptional(request.reference());
            if ((method == PaymentMethod.DEBIT || method == PaymentMethod.CREDIT) && reference == null) {
                throw new BadRequestException("reference is required for manual debit and credit refunds");
            }
            Payment originalPayment = null;
            if (request.originalPaymentId() != null) {
                if (!referencedPayments.add(request.originalPaymentId())) {
                    throw new BadRequestException("originalPaymentId may only appear once per refund");
                }
                originalPayment = sale.getPayments().stream()
                        .filter(payment -> payment.getId().equals(request.originalPaymentId()))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("Original payment not found"));
                if (originalPayment.getMethod() != method) {
                    throw new BadRequestException("Refund payment method must match original payment method");
                }
                requireOriginalPaymentBalance(originalPayment, amount);
            }
            refund.addPayment(new RefundPayment(refund, originalPayment, method, amount, sale.getCurrencyCode(), reference, cleanOptional(request.notes())));
        }
    }

    private void requireOriginalPaymentBalance(Payment originalPayment, BigDecimal amount) {
        BigDecimal alreadyRefunded = refundPaymentRepository.refundedAmountForOriginalPayment(originalPayment.getId());
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        BigDecimal requestedTotal = alreadyRefunded.setScale(MONEY_SCALE)
                .add(amount)
                .setScale(MONEY_SCALE);
        if (requestedTotal.compareTo(originalPayment.getAmount()) > 0) {
            throw new ConflictException("Refund payment amount cannot exceed original payment balance");
        }
    }

    private static void requirePaymentsMatchRefundTotal(Refund refund) {
        BigDecimal paid = refund.getPayments().stream()
                .map(RefundPayment::getAmount)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add)
                .setScale(MONEY_SCALE);
        if (paid.compareTo(refund.getTotalAmount()) != 0) {
            throw new ConflictException("Refund payments must equal refund total");
        }
    }

    private static void addTaxSnapshots(Refund refund, Return returnRecord) {
        for (ReturnItem item : returnRecord.getItems()) {
            refund.addItemTax(new RefundItemTax(refund, item));
        }
    }

    private void restoreInventory(Refund refund, Authentication authentication) {
        for (ReturnItem item : refund.getReturnRecord().getItems()) {
            if (!restoresInventory(item)) {
                continue;
            }
            inventoryService.recordStockChange(new InventoryStockChangeRequest(
                    refund.getStore().getId(),
                    item.getProduct().getId(),
                    InventoryTransactionType.RETURN,
                    item.getQuantity(),
                    REFUND_REFERENCE_TYPE,
                    refund.getId(),
                    item.getProductName(),
                    refund.getOccurredAt(),
                    null), authentication);
        }
    }

    private void appendCashLedger(Refund refund, User actor) {
        for (RefundPayment payment : refund.getPayments()) {
            if (payment.getMethod() != PaymentMethod.CASH) {
                continue;
            }
            cashLedgerService.append(new CashLedgerEntryCommand(
                    refund.getStore(),
                    refund.getRegister(),
                    refund.getRegisterSession(),
                    CashLedgerSourceType.CASH_REFUND,
                    payment.getId(),
                    CashLedgerDirection.OUT,
                    payment.getAmount(),
                    refund.getCurrencyCode(),
                    refund.getBusinessDate(),
                    refund.getOccurredAt(),
                    actor,
                    operationId(refund.getId(), payment.getId()),
                    "Cash refund"));
        }
    }

    private void markSaleRefunded(Sale sale) {
        boolean fullRefund = !sale.getItems().isEmpty();
        for (SaleItem item : sale.getItems()) {
            BigDecimal quantity = refundRepository.refundedQuantityForSaleItem(item.getId());
            BigDecimal refunded = quantity == null ? BigDecimal.ZERO.setScale(4) : quantity.setScale(4);
            if (refunded.compareTo(item.getQuantity()) < 0) {
                fullRefund = false;
                break;
            }
        }
        sale.markRefundStatus(fullRefund);
    }

    private static boolean restoresInventory(ReturnItem item) {
        String capabilities = item.getOriginalProductCapabilities();
        if (capabilities != null && !capabilities.isBlank()) {
            Set<String> snapshotCapabilities = Set.of(capabilities.split(","));
            return snapshotCapabilities.contains(ProductCapability.TRACK_INVENTORY.name());
        }
        return item.getProduct().isInventoryTrackingEnabled();
    }

    private static Specification<Refund> equalUuid(String association, String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(association).get(field), value);
    }

    private static void requireRefundableSale(Sale sale) {
        if (sale.getStatus() != SaleStatus.COMPLETED
                && sale.getStatus() != SaleStatus.PARTIALLY_REFUNDED
                && sale.getStatus() != SaleStatus.REFUNDED) {
            throw new ConflictException("Refunds can only be created for completed sales");
        }
    }

    private static void requireOpenRegisterSession(Sale sale) {
        if (sale.getRegisterSession().getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
    }

    private static void validateUserCanUseSession(User actor, Sale sale, Authentication authentication) {
        if (hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_MANAGER")) {
            return;
        }
        if (!sale.getRegisterSession().getAssignedCashier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Refund user must be assigned to this register session");
        }
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

    private Refund save(Refund refund) {
        try {
            return refundRepository.saveAndFlush(refund);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Refund was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Refund could not be recorded");
        }
    }

    private Sale saveSale(Sale sale) {
        try {
            return saleRepository.saveAndFlush(sale);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Sale was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Sale could not be updated");
        }
    }

    private void audit(User actor, RefundResponse response, SaleStatus saleStatus) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.REFUND_CREATED,
                "REFUND",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                "Sale status: " + saleStatus.name()));
    }

    private String requestBody(RefundCreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize refund request", exception);
        }
    }

    private String responseBody(RefundResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize refund response", exception);
        }
    }

    private static UUID operationId(UUID refundId, UUID paymentId) {
        return UUID.nameUUIDFromBytes((refundId + ":cash-refund:" + paymentId).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
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

    private static BigDecimal normalizeAmount(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new BadRequestException(fieldName + " must be greater than zero");
        }
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(fieldName + " may include no more than 2 decimal places");
        }
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
