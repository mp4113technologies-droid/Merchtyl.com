package com.merchtyl.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyOperationResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockCountService {
    private static final Logger log = LoggerFactory.getLogger(StockCountService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int QUANTITY_SCALE = 4;
    private static final String STOCK_COUNT_REFERENCE_TYPE = "STOCK_COUNT";
    private static final String POST_ENDPOINT = "POST /api/v1/inventory/counts/{id}/post";

    private final StockCountRepository stockCountRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Autowired(required = false)
    private StoreAccessService storeAccessService;

    @Autowired
    public StockCountService(
            StockCountRepository stockCountRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryService inventoryService,
            UserRepository userRepository,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this(stockCountRepository, storeRepository, productRepository, balanceRepository, inventoryService, userRepository,
                auditService, idempotencyService, objectMapper, Clock.systemUTC());
    }

    StockCountService(
            StockCountRepository stockCountRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryService inventoryService,
            UserRepository userRepository,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.stockCountRepository = stockCountRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public StockCountResponse create(StockCountCreateRequest request, Authentication authentication) {
        Store store = findStore(request.storeId());
        User actor = actor(authentication);
        StockCount count = new StockCount(store, cleanRequired(request.reference(), "reference"), cleanOptional(request.notes()), actor);
        requireStoreScope(authentication, store.getId());
        HashSet<UUID> productIds = new HashSet<>();

        for (StockCountLineCreateRequest lineRequest : requireLines(request.lines())) {
            if (!productIds.add(lineRequest.productId())) {
                throw new BadRequestException("Product can only appear once in a stock count");
            }
            Product product = findTrackedProduct(lineRequest.productId());
            var balance = balanceRepository.findByStoreIdAndProductId(store.getId(), product.getId()).orElse(null);
            BigDecimal expectedQuantity = balance == null
                    ? BigDecimal.ZERO.setScale(QUANTITY_SCALE)
                    : normalizeNonNegativeQuantity(balance.getQuantityOnHand(), "expectedQuantity");
            BigDecimal countedQuantity = lineRequest.countedQuantity() == null
                    ? null
                    : normalizeNonNegativeQuantity(lineRequest.countedQuantity(), "countedQuantity");
            count.addLine(new StockCountLine(
                    count,
                    product,
                    expectedQuantity,
                    countedQuantity,
                    balance == null ? null : balance.getVersion()));
        }

        stockCountRepository.saveAndFlush(count);
        audit(actor, AuditAction.STOCK_COUNT_CREATED, count.getId(), store.getId(), null, StockCountResponse.from(count), count.getReference());
        if (count.getLines().stream().allMatch(line -> line.getCountedQuantity() != null)) {
            return saveCount(count, actor, authentication);
        }
        return StockCountResponse.from(count);
    }

    @Transactional
    public StockCountResponse enterCountedQuantities(UUID id, StockCountUpdateLinesRequest request, Authentication authentication) {
        StockCount count = findCountForUpdate(id);
        User actor = actor(authentication);
        requireStoreScope(authentication, count.getStore().getId());
        Map<UUID, StockCountLine> linesById = count.getLines().stream()
                .collect(Collectors.toMap(StockCountLine::getId, Function.identity()));
        for (StockCountLineCountRequest lineRequest : requireCountLines(request.lines())) {
            StockCountLine line = linesById.get(lineRequest.lineId());
            if (line == null) {
                throw new NotFoundException("Stock count line not found");
            }
            line.enterCountedQuantity(normalizeNonNegativeQuantity(lineRequest.countedQuantity(), "countedQuantity"));
        }
        if (count.getLines().stream().anyMatch(line -> line.getCountedQuantity() == null)) {
            throw new BadRequestException("All count lines require an actual quantity");
        }
        return saveCount(count, actor, authentication);
    }

    @Transactional
    public StockCountResponse review(UUID id, StockCountReviewRequest request, Authentication authentication) {
        StockCount count = findCountForUpdate(id);
        User actor = requireActor(authentication);
        requireStoreScope(authentication, count.getStore().getId());
        return saveCount(count, actor, authentication);
    }

    public IdempotencyResult postIdempotently(
            UUID id,
            StockCountPostRequest request,
            String idempotencyKey,
            Authentication authentication) {
        User actor = requireActor(authentication);
        String requestBody = requestBody(request);
        return idempotencyService.execute(actor.getId(), POST_ENDPOINT, idempotencyKey, requestBody, () -> {
            StockCountResponse response = post(id, request, actor, authentication);
            return new IdempotencyOperationResponse(
                    200,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    @Transactional
    StockCountResponse post(UUID id, StockCountPostRequest request, User actor, Authentication authentication) {
        StockCount count = findCountForUpdate(id);
        requireStoreScope(authentication, count.getStore().getId());
        return saveCount(count, actor, authentication);
    }

    private StockCountResponse saveCount(StockCount count, User actor, Authentication authentication) {
        StockCountResponse before = StockCountResponse.from(count);
        Instant savedAt = Instant.now(clock);
        log.info("inventory_event event=STOCK_COUNT_SAVE_REQUESTED store_id={} count_id={} actor_user_id={} line_count={}",
                count.getStore().getId(), count.getId(), actor == null ? null : actor.getId(), count.getLines().size());
        for (StockCountLine line : count.getLines()) {
            if (line.getCountedQuantity() == null) {
                throw new BadRequestException("All count lines require an actual quantity");
            }
            InventoryBalance balance = balanceRepository.findByStoreIdAndProductId(count.getStore().getId(), line.getProduct().getId()).orElse(null);
            BigDecimal previousQuantity = balance == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : balance.getQuantityOnHand();
            Long balanceVersion = balance == null ? null : balance.getVersion();
            BigDecimal countedQuantity = normalizeNonNegativeQuantity(line.getCountedQuantity(), "countedQuantity");
            line.recount(previousQuantity, countedQuantity, balanceVersion);
            BigDecimal difference = line.getVarianceQuantity();
            if (difference.signum() == 0) {
                line.completePost(null, countedQuantity);
                continue;
            }
            InventoryTransactionType transactionType = difference.signum() > 0
                    ? InventoryTransactionType.STOCK_COUNT_INCREASE : InventoryTransactionType.STOCK_COUNT_DECREASE;
            InventoryTransactionResponse transaction = inventoryService.recordStockChange(new InventoryStockChangeRequest(
                    count.getStore().getId(), line.getProduct().getId(), transactionType, difference,
                    STOCK_COUNT_REFERENCE_TYPE, count.getId(), count.getReference(), savedAt, balanceVersion), authentication);
            line.completePost(transaction.id(), transaction.resultingQuantity());
        }
        count.markSaved(actor, savedAt);
        StockCountResponse response;
        try {
            response = StockCountResponse.from(stockCountRepository.saveAndFlush(count));
        } catch (DataIntegrityViolationException exception) {
            log.error("inventory_event event=STOCK_COUNT_SAVE_FAILED store_id={} count_id={}", count.getStore().getId(), count.getId(), exception);
            throw new ConflictException("We couldn't update the stock count. Please try again.");
        }
        audit(actor, AuditAction.STOCK_COUNT_UPDATED, response.id(), response.storeId(), before, response, response.reference());
        log.info("inventory_event event=STOCK_COUNT_UPDATED store_id={} count_id={} actor_user_id={} line_count={}",
                response.storeId(), response.id(), actor == null ? null : actor.getId(), response.lines().size());
        return response;
    }

    private static void requirePostable(StockCount count) {
        if (count.getStatus() == StockCountStatus.POSTED) {
            throw new ConflictException("Stock count is already posted");
        }
        if (count.getStatus() != StockCountStatus.IN_REVIEW) {
            throw new ConflictException("Stock count must be in review before posting");
        }
    }

    private void requireStoreScope(Authentication authentication, UUID storeId) {
        if (storeAccessService != null) {
            storeAccessService.requireProductManagementScope(authentication, java.util.Set.of(storeId));
        }
    }

    @Transactional(readOnly = true)
    public StockCountResponse get(UUID id) {
        return StockCountResponse.from(findCount(id));
    }

    @Transactional(readOnly = true)
    public StockCountResponse get(UUID id, Authentication authentication) {
        StockCount count = findCount(id);
        requireStoreScope(authentication, count.getStore().getId());
        return StockCountResponse.from(count);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockCountResponse> search(StockCountSearchRequest request) {
        return search(request, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockCountResponse> search(StockCountSearchRequest request, Authentication authentication) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = stockCountRepository.findAll(
                specification(request).and(accessibleCounts(authentication)),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(StockCountResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private Specification<StockCount> accessibleCounts(Authentication authentication) {
        if (storeAccessService == null || authentication == null) return null;
        User actor = storeAccessService.currentTenantUser(authentication);
        if (StoreAccessService.isOwner(storeAccessService.roles(actor))) {
            return (root, query, builder) -> builder.equal(root.get("store").get("tenantId"), actor.getTenantId());
        }
        Set<UUID> storeIds = storeAccessService.getActiveManagedStoreIds(actor.getId());
        return (root, query, builder) -> root.get("store").get("id").in(storeIds);
    }

    private void requireCurrentBalance(StockCount count, StockCountLine line) {
        var balance = balanceRepository.findByStoreIdAndProductId(count.getStore().getId(), line.getProduct().getId()).orElse(null);
        BigDecimal currentQuantity = balance == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : balance.getQuantityOnHand();
        if (currentQuantity.compareTo(line.getExpectedQuantity()) != 0) {
            throw new ConflictException("Inventory changed since count was created");
        }
        if (line.getBalanceVersion() != null && (balance == null || line.getBalanceVersion() != balance.getVersion())) {
            throw new ConflictException("Inventory changed since count was created");
        }
    }

    private Long currentBalanceVersionForPost(StockCount count, StockCountLine line) {
        return balanceRepository.findByStoreIdAndProductId(count.getStore().getId(), line.getProduct().getId())
                .map(InventoryBalance::getVersion)
                .orElse(null);
    }

    private Store findStore(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Product findTrackedProduct(UUID productId) {
        Product product = findProduct(productId);
        if (!product.isInventoryTrackingEnabled()) {
            throw new BadRequestException("Product does not track inventory");
        }
        return product;
    }

    private Product findProduct(UUID productId) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private StockCount findCount(UUID id) {
        if (id == null) {
            throw new BadRequestException("stock count id is required");
        }
        return stockCountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Stock count not found"));
    }

    private StockCount findCountForUpdate(UUID id) {
        if (id == null) {
            throw new BadRequestException("stock count id is required");
        }
        return stockCountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Stock count not found"));
    }

    private User requireActor(Authentication authentication) {
        User actor = actor(authentication);
        if (actor == null) {
            throw new BadRequestException("Authenticated user is required");
        }
        return actor;
    }

    private User actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private void audit(User actor, AuditAction action, UUID entityId, UUID storeId, Object before, Object after, String summary) {
        auditService.record(new CreateAuditRecordCommand(
                actor == null ? null : actor.getId(),
                action,
                "STOCK_COUNT",
                entityId,
                storeId,
                null,
                before,
                after,
                summary));
    }

    private String requestBody(StockCountPostRequest request) {
        try {
            return objectMapper.writeValueAsString(request == null ? new StockCountPostRequest(null) : request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stock count post request must be JSON serializable", exception);
        }
    }

    private String responseBody(StockCountResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stock count response must be JSON serializable", exception);
        }
    }

    private static List<StockCountLineCreateRequest> requireLines(List<StockCountLineCreateRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("At least one count line is required");
        }
        return lines;
    }

    private static List<StockCountLineCountRequest> requireCountLines(List<StockCountLineCountRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("At least one count line is required");
        }
        return lines;
    }

    private static BigDecimal normalizeNonNegativeQuantity(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(fieldName + " must be zero or greater");
        }
        try {
            return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(fieldName + " supports up to 4 decimal places");
        }
    }

    private static Specification<StockCount> specification(StockCountSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalEnum("status", request.status()))
                .and(createdAtGreaterThanOrEqualTo(request.createdFrom()))
                .and(createdAtLessThanOrEqualTo(request.createdTo()));
    }

    private static Specification<StockCount> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<StockCount> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<StockCount> createdAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), value);
    }

    private static Specification<StockCount> createdAtLessThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), value);
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
}
