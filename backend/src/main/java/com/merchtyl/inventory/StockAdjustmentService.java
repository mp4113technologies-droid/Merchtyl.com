package com.merchtyl.inventory;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StockAdjustmentService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int QUANTITY_SCALE = 4;

    private final StockAdjustmentRepository adjustmentRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public StockAdjustmentService(
            StockAdjustmentRepository adjustmentRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            AuditService auditService) {
        this(adjustmentRepository, storeRepository, productRepository, userRepository, inventoryService, auditService, Clock.systemUTC());
    }

    StockAdjustmentService(
            StockAdjustmentRepository adjustmentRepository,
            StoreRepository storeRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            AuditService auditService,
            Clock clock) {
        this.adjustmentRepository = adjustmentRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public StockAdjustmentResponse create(StockAdjustmentRequest request, Authentication authentication) {
        Store store = findStore(request.storeId());
        User actor = actor(authentication);
        Instant approvedAt = Instant.now(clock);
        StockAdjustment adjustment = new StockAdjustment(
                store,
                cleanRequired(request.reason(), "reason"),
                cleanOptional(request.notes()),
                actor,
                approvedAt,
                cleanOptional(request.approvalNotes()));

        for (StockAdjustmentLineRequest lineRequest : requireLines(request.lines())) {
            Product product = findProduct(lineRequest.productId());
            BigDecimal quantity = normalizeQuantity(lineRequest.quantity());
            StockAdjustmentType adjustmentType = requireAdjustmentType(lineRequest.adjustmentType());
            BigDecimal quantityDelta = quantity.multiply(BigDecimal.valueOf(adjustmentType.directionMultiplier()))
                    .setScale(QUANTITY_SCALE);
            StockAdjustmentLine line = new StockAdjustmentLine(
                    adjustment,
                    product,
                    adjustmentType,
                    quantity,
                    quantityDelta);
            InventoryTransactionResponse transaction = inventoryService.recordStockChange(new InventoryStockChangeRequest(
                    store.getId(),
                    product.getId(),
                    adjustmentType.transactionType(),
                    quantityDelta,
                    "STOCK_ADJUSTMENT",
                    adjustment.getId(),
                    adjustment.getReason(),
                    approvedAt,
                    lineRequest.balanceVersion()), authentication);
            line.complete(transaction.id(), transaction.resultingQuantity());
            adjustment.addLine(line);
        }

        StockAdjustmentResponse response = StockAdjustmentResponse.from(adjustmentRepository.saveAndFlush(adjustment));
        audit(response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentResponse> search(StockAdjustmentSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = adjustmentRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(StockAdjustmentResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
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

    private User actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private void audit(StockAdjustmentResponse response) {
        auditService.record(new CreateAuditRecordCommand(
                response.approvedByUserId(),
                AuditAction.STOCK_ADJUSTMENT_CREATED,
                "STOCK_ADJUSTMENT",
                response.id(),
                response.storeId(),
                null,
                null,
                response,
                response.reason()));
    }

    private static List<StockAdjustmentLineRequest> requireLines(List<StockAdjustmentLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("At least one adjustment line is required");
        }
        return lines;
    }

    private static StockAdjustmentType requireAdjustmentType(StockAdjustmentType adjustmentType) {
        if (adjustmentType == null) {
            throw new BadRequestException("adjustmentType is required");
        }
        return adjustmentType;
    }

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("quantity is required");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("quantity must be greater than zero");
        }
        try {
            return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("quantity supports up to 4 decimal places");
        }
    }

    private static Specification<StockAdjustment> specification(StockAdjustmentSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalEnum("approvalStatus", request.approvalStatus()))
                .and(createdAtGreaterThanOrEqualTo(request.createdFrom()))
                .and(createdAtLessThanOrEqualTo(request.createdTo()));
    }

    private static Specification<StockAdjustment> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<StockAdjustment> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<StockAdjustment> createdAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), value);
    }

    private static Specification<StockAdjustment> createdAtLessThanOrEqualTo(Instant value) {
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
