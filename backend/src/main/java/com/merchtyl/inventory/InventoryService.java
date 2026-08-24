package com.merchtyl.inventory;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int QUANTITY_SCALE = 4;

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    @Autowired private StoreAccessService storeAccessService;

    @Autowired
    public InventoryService(
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this(balanceRepository, transactionRepository, storeRepository, productRepository, userRepository, Clock.systemUTC());
    }

    InventoryService(
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            Clock clock) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public InventoryTransactionResponse recordStockChange(InventoryStockChangeRequest request, Authentication authentication) {
        log.info("inventory_event event=INVENTORY_STOCK_ADD_REQUESTED store_id={} product_id={} transaction_type={} actor={}",
                request.storeId(), request.productId(), request.transactionType(), authentication == null ? null : authentication.getName());
        if (storeAccessService != null && requiresInventoryManagement(request.transactionType())) {
            try {
                storeAccessService.requireProductManagementScope(authentication, java.util.Set.of(request.storeId()));
            } catch (RuntimeException exception) {
                log.warn("inventory_event event=INVENTORY_STOCK_DENIED store_id={} product_id={} actor={} reason={}",
                        request.storeId(), request.productId(), authentication == null ? null : authentication.getName(), exception.getMessage());
                throw exception;
            }
        }
        Store store = findStore(request.storeId());
        Product product = findProduct(request.productId());
        User actor = authentication == null ? null : userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (actor == null || actor.getTenantId() == null || !actor.getTenantId().equals(store.getTenantId())
                || !actor.getTenantId().equals(product.getTenantId())) {
            throw new NotFoundException("Store product not found");
        }
        if (!product.isInventoryTrackingEnabled()) {
            throw new BadRequestException("Product does not track inventory");
        }

        BigDecimal quantityDelta = normalizeQuantityDelta(request.quantityDelta());
        requireValidDirection(request.transactionType(), quantityDelta);
        Instant occurredAt = request.occurredAt() == null ? Instant.now(clock) : request.occurredAt();

        InventoryBalance balance = balanceRepository.findByStoreIdAndProductId(store.getId(), product.getId())
                .orElseGet(() -> new InventoryBalance(store, product, BigDecimal.ZERO.setScale(QUANTITY_SCALE), occurredAt));
        requireCurrentVersion(balance, request.balanceVersion());

        BigDecimal resultingQuantity = balance.getQuantityOnHand().add(quantityDelta).setScale(QUANTITY_SCALE);
        if (resultingQuantity.compareTo(BigDecimal.ZERO) < 0
                && request.transactionType() != InventoryTransactionType.SALE
                && !store.isNegativeStockAllowed()) {
            throw new ConflictException("Store does not allow negative stock");
        }

        balance.apply(quantityDelta, occurredAt);
        InventoryBalance savedBalance = saveBalance(balance);
        InventoryTransaction transaction = new InventoryTransaction(
                savedBalance,
                request.transactionType(),
                quantityDelta,
                resultingQuantity,
                cleanOptionalUpper(request.referenceType()),
                request.referenceId(),
                cleanOptional(request.reason()),
                actorUserId(authentication),
                occurredAt);
        InventoryTransactionResponse response = InventoryTransactionResponse.from(transactionRepository.saveAndFlush(transaction));
        log.info("inventory_event event={} transaction_type={} store_id={} product_id={} quantity_delta={} resulting_quantity={} reference_type={} reference_id={}",
                inventoryEventName(request.transactionType(), quantityDelta),
                request.transactionType(),
                store.getId(),
                product.getId(),
                quantityDelta,
                resultingQuantity,
                cleanOptionalUpper(request.referenceType()),
                request.referenceId());
        log.info("inventory_event event=INVENTORY_STOCK_POSTED store_id={} product_id={} transaction_id={} actor_user_id={} resulting_quantity={}",
                store.getId(), product.getId(), response.id(), response.actorUserId(), response.resultingQuantity());
        return response;
    }

    @Transactional(readOnly = true)
    public InventoryBalanceResponse currentStock(UUID storeId, UUID productId) {
        requireExistingStore(storeId);
        requireExistingProduct(productId);
        return balanceRepository.findByStoreIdAndProductId(storeId, productId)
                .map(InventoryBalanceResponse::from)
                .orElseGet(() -> InventoryBalanceResponse.zero(storeId, productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceResponse> searchBalances(InventoryBalanceSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = balanceRepository.findAll(
                balanceSpecification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(InventoryBalanceResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> searchTransactions(InventoryTransactionSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = transactionRepository.findAll(
                transactionSpecification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "occurredAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(InventoryTransactionResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private InventoryBalance saveBalance(InventoryBalance balance) {
        try {
            return balanceRepository.saveAndFlush(balance);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Inventory balance was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Inventory balance already exists");
        }
    }

    private Store findStore(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Product findProduct(UUID productId) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private void requireExistingStore(UUID storeId) {
        findStore(storeId);
    }

    private void requireExistingProduct(UUID productId) {
        findProduct(productId);
    }

    private void requireCurrentVersion(InventoryBalance balance, Long requestedVersion) {
        if (requestedVersion != null && requestedVersion != balance.getVersion()) {
            throw new ConflictException("Inventory balance was modified by another transaction");
        }
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private static BigDecimal normalizeQuantityDelta(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("quantityDelta is required");
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new BadRequestException("quantityDelta must not be zero");
        }
        try {
            return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("quantityDelta supports up to 4 decimal places");
        }
    }

    private static void requireValidDirection(InventoryTransactionType type, BigDecimal quantityDelta) {
        if (type == null) {
            throw new BadRequestException("transactionType is required");
        }
        boolean positive = quantityDelta.compareTo(BigDecimal.ZERO) > 0;
        switch (type) {
            case OPENING_STOCK, PURCHASE, RETURN, ADJUSTMENT_INCREASE, STOCK_COUNT_INCREASE, TRANSFER_IN -> {
                if (!positive) {
                    throw new BadRequestException(type.name() + " requires a positive quantityDelta");
                }
            }
            case SALE, ADJUSTMENT_DECREASE, STOCK_COUNT_DECREASE, DAMAGED, EXPIRED, TRANSFER_OUT -> {
                if (positive) {
                    throw new BadRequestException(type.name() + " requires a negative quantityDelta");
                }
            }
            case VOID_REVERSAL -> {
            }
        }
    }

    private static boolean requiresInventoryManagement(InventoryTransactionType type) {
        return type == InventoryTransactionType.OPENING_STOCK
                || type == InventoryTransactionType.PURCHASE
                || type == InventoryTransactionType.ADJUSTMENT_INCREASE
                || type == InventoryTransactionType.ADJUSTMENT_DECREASE
                || type == InventoryTransactionType.DAMAGED
                || type == InventoryTransactionType.EXPIRED
                || type == InventoryTransactionType.STOCK_COUNT_INCREASE
                || type == InventoryTransactionType.STOCK_COUNT_DECREASE
                || type == InventoryTransactionType.TRANSFER_IN
                || type == InventoryTransactionType.TRANSFER_OUT;
    }

    private static String inventoryEventName(InventoryTransactionType type, BigDecimal quantityDelta) {
        if (type == InventoryTransactionType.STOCK_COUNT_INCREASE || type == InventoryTransactionType.STOCK_COUNT_DECREASE) {
            return "Inventory Count";
        }
        if (type == InventoryTransactionType.ADJUSTMENT_INCREASE || type == InventoryTransactionType.ADJUSTMENT_DECREASE) {
            return "Inventory Adjustment";
        }
        return quantityDelta.compareTo(BigDecimal.ZERO) > 0 ? "Inventory Increased" : "Inventory Reduced";
    }

    private static Specification<InventoryBalance> balanceSpecification(InventoryBalanceSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalReference("product", request.productId()));
    }

    private static Specification<InventoryTransaction> transactionSpecification(InventoryTransactionSearchRequest request) {
        return Specification
                .where(transactionReference("store", request.storeId()))
                .and(transactionReference("product", request.productId()))
                .and(equalEnum("transactionType", request.transactionType()))
                .and(equalUuid("referenceId", request.referenceId()))
                .and(occurredAtGreaterThanOrEqualTo(request.occurredFrom()))
                .and(occurredAtLessThanOrEqualTo(request.occurredTo()));
    }

    private static Specification<InventoryBalance> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<InventoryTransaction> transactionReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<InventoryTransaction> equalUuid(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<InventoryTransaction> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<InventoryTransaction> occurredAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), value);
    }

    private static Specification<InventoryTransaction> occurredAtLessThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), value);
    }

    private static String cleanOptionalUpper(String value) {
        String cleaned = cleanOptional(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
