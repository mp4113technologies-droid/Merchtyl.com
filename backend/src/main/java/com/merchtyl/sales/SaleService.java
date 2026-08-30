package com.merchtyl.sales;

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
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductVariant;
import com.merchtyl.product.ProductVariantRepository;
import com.merchtyl.product.StoreProduct;
import com.merchtyl.product.StoreProductRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.tax.TaxCalculationRequest;
import com.merchtyl.tax.TaxCalculationResponse;
import com.merchtyl.tax.TaxEngine;
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
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class SaleService {
    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String COMPLETE_ENDPOINT = "POST /api/v1/sales/{id}/complete";
    private static final String SALE_REFERENCE_TYPE = "SALE";

    private final SaleRepository saleRepository;
    private final RegisterSessionRepository registerSessionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SaleItemHandlerRegistry saleItemHandlerRegistry;
    private final TaxEngine taxEngine;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final CashLedgerService cashLedgerService;
    private final TransactionOperations transactions;
    private final Clock clock;
    @Autowired
    private SaleAdjustmentRepository saleAdjustmentRepository;
    @Autowired
    private StoreProductRepository storeProductRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    public SaleService(
            SaleRepository saleRepository,
            RegisterSessionRepository registerSessionRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            SaleItemHandlerRegistry saleItemHandlerRegistry,
            TaxEngine taxEngine,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            InventoryService inventoryService,
            CashLedgerService cashLedgerService,
            TransactionOperations idempotencyTransactionOperations) {
        this(saleRepository, registerSessionRepository, productRepository, userRepository, saleItemHandlerRegistry, taxEngine, auditService,
                idempotencyService, objectMapper, inventoryService, cashLedgerService, idempotencyTransactionOperations, Clock.systemUTC());
    }

    SaleService(
            SaleRepository saleRepository,
            RegisterSessionRepository registerSessionRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            SaleItemHandlerRegistry saleItemHandlerRegistry,
            TaxEngine taxEngine,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            InventoryService inventoryService,
            CashLedgerService cashLedgerService,
            TransactionOperations transactions,
            Clock clock) {
        this.saleRepository = saleRepository;
        this.registerSessionRepository = registerSessionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.saleItemHandlerRegistry = saleItemHandlerRegistry;
        this.taxEngine = taxEngine;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.cashLedgerService = cashLedgerService;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public SaleResponse createDraft(SaleCreateDraftRequest request, Authentication authentication) {
        User actor = actor(authentication);
        RegisterSession session = findOpenSession(request.registerSessionId());
        validateUserCanUseSession(actor, session, authentication);
        Sale sale = new Sale(
                session.getStore(),
                session.getRegister(),
                session,
                actor,
                request.customerId(),
                Instant.now(clock).atZone(ZoneId.of(session.getStore().getTimezone())).toLocalDate(),
                cleanOptional(request.saleChannel()),
                session.getStore().getCurrencyCode(),
                session.getStore().isPricesIncludeTax());
        Sale saved = save(sale);
        SaleResponse response = SaleResponse.from(saved);
        audit(actor, AuditAction.SALE_DRAFT_CREATED, response, null);
        return response;
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        return SaleResponse.from(findSale(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(SaleSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = saleRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(SaleResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional
    public SaleResponse addItem(UUID saleId, SaleAddItemRequest request, Authentication authentication) {
        return addItem(saleId, request, authentication, null, null);
    }

    @Transactional
    public SaleResponse addFoodMenuItem(UUID saleId, UUID storeId, UUID productId, BigDecimal quantity,
                                        BigDecimal menuPrice, Authentication authentication) {
        return addItem(saleId, new SaleAddItemRequest(productId, quantity, null, null, false, false,
                null, null, null, null), authentication, storeId, menuPrice);
    }

    private SaleResponse addItem(UUID saleId, SaleAddItemRequest request, Authentication authentication,
                                 UUID requiredStoreId, BigDecimal trustedUnitPrice) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        if (requiredStoreId != null && !sale.getStore().getId().equals(requiredStoreId)) {
            throw new ForbiddenOperationException("Food menu does not belong to the sale store");
        }
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        ResolvedStoreProduct storeProduct = storeProduct(sale, request.productId());
        Product product = storeProduct.product();
        ProductVariant variant = request.variantId() == null ? null : productVariantRepository.findById(request.variantId())
                .filter(candidate -> candidate.getProduct().getId().equals(product.getId()) && candidate.isActive())
                .orElseThrow(() -> new NotFoundException("PRODUCT_VARIANT_NOT_AVAILABLE"));
        SaleItem existing = sale.getItems().stream()
                .filter(candidate -> candidate.getProduct().getId().equals(product.getId()))
                .filter(candidate -> java.util.Objects.equals(
                        candidate.getVariant() == null ? null : candidate.getVariant().getId(), request.variantId()))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.updateQuantity(existing.getQuantity().add(normalizeQuantity(request.quantity())));
            saleItemHandlerRegistry.validate(existing.validationRequest());
            recalculate(sale, authentication);
            SaleResponse response = SaleResponse.from(save(sale));
            audit(actor, AuditAction.SALE_ITEM_UPDATED, response, existing.getProductName());
            return response;
        }
        SaleItem item = new SaleItem(
                sale,
                product,
                variant,
                normalizeQuantity(request.quantity()),
                normalizeMoney(trustedUnitPrice != null ? trustedUnitPrice : (variant == null ? storeProduct.sellingPrice() : variant.getPrice()), "unitPrice"),
                moneyZero(),
                false,
                Boolean.TRUE.equals(request.ageVerified()),
                cleanOptional(request.serialNumber()),
                cleanOptional(request.externalReference()),
                request.customerId() == null ? sale.getCustomerId() : request.customerId(),
                cleanOptional(request.paymentMethodCode()));
        saleItemHandlerRegistry.validate(item.validationRequest());
        sale.addItem(item);
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_ITEM_ADDED, response, item.getProductName());
        if (product.hasCapability(com.merchtyl.product.ProductCapability.REQUIRE_AGE_VERIFICATION)) {
            audit(actor, AuditAction.AGE_VERIFICATION_CONFIRMED, response,
                    "productId=" + product.getId() + ",variantId=" + (variant == null ? "" : variant.getId())
                            + ",minimumAge=" + product.getMinimumAge());
        }
        return response;
    }

    @Transactional
    public SaleResponse updateQuantity(UUID saleId, UUID itemId, SaleUpdateQuantityRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        item.updateQuantity(normalizeQuantity(request.quantity()));
        saleItemHandlerRegistry.validate(item.validationRequest());
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_ITEM_UPDATED, response, item.getProductName());
        return response;
    }

    @Transactional
    public SaleResponse removeItem(UUID saleId, UUID itemId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        String productName = item.getProductName();
        sale.removeItem(item);
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_ITEM_REMOVED, response, productName);
        return response;
    }

    @Transactional
    public SaleResponse overridePrice(UUID saleId, UUID itemId, PriceOverrideRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        BigDecimal original = item.getUnitPrice();
        BigDecimal adjusted = normalizeMoney(request.unitPrice(), "unitPrice");
        item.overrideUnitPrice(adjusted);
        saleItemHandlerRegistry.validate(item.validationRequest());
        saleAdjustmentRepository.save(new SaleAdjustment(sale, item, request.type(), original, adjusted, null,
                cleanRequired(request.reasonCode(), "reasonCode"), cleanOptional(request.reason()), actor,
                actor, Instant.now(clock), MDC.get("correlationId")));
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.PRICE_OVERRIDE_APPROVED, response, request.reasonCode());
        return response;
    }

    @Transactional
    public SaleResponse applyLineDiscount(UUID saleId, UUID itemId, LineDiscountRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        BigDecimal lineBase = item.getUnitPrice().multiply(item.getQuantity());
        BigDecimal discount = request.type() == SaleAdjustmentType.DISCOUNT_PERCENTAGE
                ? lineBase.multiply(request.value()).divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_UP)
                : normalizeMoney(request.value(), "value");
        if (discount.compareTo(lineBase) > 0) {
            throw new BadRequestException("Discount cannot exceed the line subtotal");
        }
        item.applyDiscount(discount);
        saleItemHandlerRegistry.validate(item.validationRequest());
        saleAdjustmentRepository.save(new SaleAdjustment(sale, item, request.type(), BigDecimal.ZERO, discount,
                request.type() == SaleAdjustmentType.DISCOUNT_PERCENTAGE ? request.value() : null,
                cleanRequired(request.reasonCode(), "reasonCode"), cleanOptional(request.reason()), actor,
                actor, Instant.now(clock), MDC.get("correlationId")));
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.LINE_DISCOUNT_APPLIED, response, request.reasonCode());
        return response;
    }

    @Transactional
    public SaleResponse hold(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        sale.hold(Instant.now(clock));
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_HELD, response, null);
        return response;
    }

    @Transactional
    public SaleResponse resume(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        if (sale.getStatus() != SaleStatus.HELD) {
            throw new ConflictException("Only held sales can be resumed");
        }
        sale.resume();
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_RESUMED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse cancel(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        sale.cancel(Instant.now(clock));
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_CANCELLED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse recalculate(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        if (sale.getStatus() != SaleStatus.DRAFT && sale.getStatus() != SaleStatus.HELD) {
            throw new ConflictException("Only draft or held sales can be recalculated");
        }
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_RECALCULATED, response, null);
        return response;
    }

    public IdempotencyResult completeIdempotently(UUID saleId, String idempotencyKey, Authentication authentication) {
        User actor = actor(authentication);
        String requestBody = "{\"saleId\":\"" + saleId + "\"}";
        return idempotencyService.execute(actor.getId(), COMPLETE_ENDPOINT, idempotencyKey, requestBody, () -> {
            SaleResponse response = transactions.execute(status -> complete(saleId, actor, authentication));
            return new IdempotencyOperationResponse(
                    200,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    @Transactional
    SaleResponse complete(UUID saleId, User actor, Authentication authentication) {
        Sale sale = findSaleForUpdate(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireOpenRegisterSession(sale);
        if (sale.getItems().isEmpty()) {
            throw new ConflictException("Sale must have at least one item before completion");
        }

        recalculate(sale, authentication);
        sale.getItems().forEach(item -> saleItemHandlerRegistry.validate(item.validationRequest()));
        requireSufficientPayments(sale);

        Instant completedAt = Instant.now(clock);
        List<SaleItem> items = sale.getItems();
        for (SaleItem item : items) {
            item.snapshotForCompletion();
            deductInventory(sale, item, completedAt, authentication);
        }
        appendCashLedgerEntries(sale, actor, completedAt);
        sale.complete(actor, completedAt);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_COMPLETED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse recordPayment(UUID saleId, SalePaymentRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        if (sale.getItems().isEmpty() || sale.getTotalAmount().signum() <= 0) {
            throw new ConflictException("Sale must have a payable total before recording payment");
        }

        BigDecimal amount = normalizeMoney(request.amount(), "amount");
        if (amount.signum() <= 0) {
            throw new BadRequestException("amount must be greater than zero");
        }
        BigDecimal balanceDue = balanceDue(sale);
        if (balanceDue.signum() <= 0) {
            throw new ConflictException("Sale is already fully paid");
        }
        if (amount.compareTo(balanceDue) > 0) {
            throw new BadRequestException("amount cannot exceed remaining balance due");
        }

        PaymentMethod method = request.method();
        if (method == null) {
            throw new BadRequestException("method is required");
        }
        BigDecimal cashTendered = null;
        BigDecimal changeDue = moneyZero();
        if (method == PaymentMethod.CASH) {
            if (request.cashTendered() == null) {
                throw new BadRequestException("cashTendered is required for cash payments");
            }
            cashTendered = normalizeMoney(request.cashTendered(), "cashTendered");
            if (cashTendered.compareTo(amount) < 0) {
                throw new BadRequestException("cashTendered must be greater than or equal to amount");
            }
            changeDue = money(cashTendered.subtract(amount));
        } else if (request.cashTendered() != null) {
            throw new BadRequestException("cashTendered is only allowed for cash payments");
        }

        String reference = cleanOptional(request.reference());
        if ((method == PaymentMethod.DEBIT || method == PaymentMethod.CREDIT) && reference == null) {
            throw new BadRequestException("reference is required for manual debit and credit payments");
        }

        Payment payment = new Payment(
                sale,
                method,
                amount,
                sale.getCurrencyCode(),
                cashTendered,
                changeDue,
                reference,
                cleanOptional(request.notes()),
                actor,
                Instant.now(clock));
        sale.addPayment(payment);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_PAYMENT_RECORDED, response, method.name());
        return response;
    }

    private void recalculate(Sale sale, Authentication authentication) {
        BigDecimal subtotal = moneyZero();
        BigDecimal discount = moneyZero();
        BigDecimal tax = moneyZero();
        BigDecimal total = moneyZero();
        for (SaleItem item : sale.getItems()) {
            saleItemHandlerRegistry.validate(item.validationRequest());
            BigDecimal lineSubtotal = money(item.getUnitPrice().multiply(item.getQuantity()));
            TaxCalculationResponse taxResponse = taxEngine.calculate(new TaxCalculationRequest(
                    sale.getStore().getId(),
                    null,
                    null,
                    item.getProduct().getId(),
                    item.getProduct().getTaxCategoryId(),
                    false,
                    sale.getBusinessDate(),
                    sale.getSaleChannel(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getDiscountAmount(),
                    sale.isPricesIncludeTax(),
                    sale.getCurrencyCode()), authentication);
            item.setCalculatedAmounts(lineSubtotal, taxResponse.taxAmount(), taxResponse.grossAmount());
            subtotal = subtotal.add(lineSubtotal);
            discount = discount.add(item.getDiscountAmount());
            tax = tax.add(taxResponse.taxAmount());
            total = total.add(taxResponse.grossAmount());
        }
        sale.setTotals(money(subtotal), money(discount), money(tax), money(total));
    }

    private Sale findSale(UUID id) {
        if (id == null) {
            throw new BadRequestException("sale id is required");
        }
        return saleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
    }

    private Sale findSaleForUpdate(UUID id) {
        if (id == null) {
            throw new BadRequestException("sale id is required");
        }
        return saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
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

    private ResolvedStoreProduct storeProduct(Sale sale, UUID productId) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }
        if (storeProductRepository == null) {
            Product product = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));
            return new ResolvedStoreProduct(product, product.getPrice());
        }
        StoreProduct mapping = storeProductRepository.findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(
                        sale.getStore().getTenantId(), sale.getStore().getId(), productId)
                .orElseThrow(() -> new BadRequestException("PRODUCT_NOT_AVAILABLE_AT_STORE"));
        return new ResolvedStoreProduct(mapping.getProduct(), mapping.getSellingPrice());
    }

    private record ResolvedStoreProduct(Product product, BigDecimal sellingPrice) {}

    private static SaleItem findItem(Sale sale, UUID itemId) {
        if (itemId == null) {
            throw new BadRequestException("item id is required");
        }
        return sale.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Sale item not found"));
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
            throw new ForbiddenOperationException("Sale user must be assigned to this register session");
        }
    }

    private static void requireDraft(Sale sale) {
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new ConflictException("Sale must be in draft status");
        }
    }

    private static void requireOpenRegisterSession(Sale sale) {
        if (sale.getRegisterSession().getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
    }

    private static void requireSufficientPayments(Sale sale) {
        if (paidAmount(sale).compareTo(sale.getTotalAmount()) < 0) {
            throw new ConflictException("Sale has insufficient payments");
        }
    }

    private static void requireNoPayments(Sale sale) {
        if (!sale.getPayments().isEmpty()) {
            throw new ConflictException("Sale payments are immutable; cart cannot be changed after payment is recorded");
        }
    }

    private static BigDecimal paidAmount(Sale sale) {
        return money(sale.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal balanceDue(Sale sale) {
        return money(sale.getTotalAmount().subtract(paidAmount(sale)).max(BigDecimal.ZERO));
    }

    private void deductInventory(Sale sale, SaleItem item, Instant completedAt, Authentication authentication) {
        Product product = item.getProduct();
        if (!product.isInventoryTrackingEnabled()) {
            return;
        }
        inventoryService.recordStockChange(new InventoryStockChangeRequest(
                sale.getStore().getId(),
                product.getId(),
                InventoryTransactionType.SALE,
                item.getQuantity().negate(),
                SALE_REFERENCE_TYPE,
                sale.getId(),
                item.getProductName(),
                completedAt,
                null), authentication);
    }

    private void appendCashLedgerEntries(Sale sale, User actor, Instant completedAt) {
        for (Payment payment : sale.getPayments()) {
            if (payment.getMethod() != PaymentMethod.CASH) {
                continue;
            }
            cashLedgerService.append(new CashLedgerEntryCommand(
                    sale.getStore(),
                    sale.getRegister(),
                    sale.getRegisterSession(),
                    CashLedgerSourceType.SALE_CASH_RECEIPT,
                    payment.getId(),
                    CashLedgerDirection.IN,
                    payment.getCashTendered(),
                    sale.getCurrencyCode(),
                    sale.getBusinessDate(),
                    completedAt,
                    actor,
                    operationId(sale.getId(), "cash-receipt", payment.getId()),
                    "Sale cash tender"));
            if (payment.getChangeDue().signum() > 0) {
                cashLedgerService.append(new CashLedgerEntryCommand(
                        sale.getStore(),
                        sale.getRegister(),
                        sale.getRegisterSession(),
                        CashLedgerSourceType.SALE_CHANGE_GIVEN,
                        payment.getId(),
                        CashLedgerDirection.OUT,
                        payment.getChangeDue(),
                        sale.getCurrencyCode(),
                        sale.getBusinessDate(),
                        completedAt,
                        actor,
                        operationId(sale.getId(), "cash-change", payment.getId()),
                        "Sale change given"));
            }
        }
    }

    private static UUID operationId(UUID saleId, String operation, UUID paymentId) {
        return UUID.nameUUIDFromBytes((saleId + ":" + operation + ":" + paymentId).getBytes(StandardCharsets.UTF_8));
    }

    private Sale save(Sale sale) {
        try {
            return saleRepository.saveAndFlush(sale);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Sale was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Sale could not be saved");
        }
    }

    private void audit(User actor, AuditAction action, SaleResponse response, String notes) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                action,
                "SALE",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                notes));
    }

    private static BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BadRequestException("quantity must be greater than zero");
        }
        try {
            return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("quantity may include no more than 4 decimal places");
        }
    }

    private static BigDecimal normalizeMoney(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new BadRequestException(fieldName + " must be zero or greater");
        }
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(fieldName + " may include no more than 2 decimal places");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanRequired(String value, String field) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) {
            throw new BadRequestException(field + " is required");
        }
        return cleaned;
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    private String responseBody(SaleResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize sale response", exception);
        }
    }

    private static Specification<Sale> specification(SaleSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("registerSession", request.registerSessionId()))
                .and(equalReference("createdBy", request.createdBy()))
                .and(equalEnum("status", request.status()));
    }

    private static Specification<Sale> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<Sale> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }
}
