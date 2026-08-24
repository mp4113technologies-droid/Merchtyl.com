package com.merchtyl.returns;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReturnService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;

    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public ReturnService(
            ReturnRepository returnRepository,
            ReturnItemRepository returnItemRepository,
            SaleRepository saleRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this(returnRepository, returnItemRepository, saleRepository, userRepository, auditService, Clock.systemUTC());
    }

    ReturnService(
            ReturnRepository returnRepository,
            ReturnItemRepository returnItemRepository,
            SaleRepository saleRepository,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock) {
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.saleRepository = saleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ReturnResponse create(ReturnCreateRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = saleRepository.findByIdForUpdate(required(request.originalSaleId(), "originalSaleId"))
                .orElseThrow(() -> new NotFoundException("Original sale not found"));
        requireCompletedSale(sale);

        String returnReason = cleanOptional(request.reason());
        Return returnRecord = new Return(sale, actor, Instant.now(clock), requireReason(returnReason));
        Set<UUID> requestedSaleItems = new HashSet<>();
        for (ReturnItemRequest itemRequest : request.items()) {
            UUID saleItemId = required(itemRequest.originalSaleItemId(), "originalSaleItemId");
            if (!requestedSaleItems.add(saleItemId)) {
                throw new BadRequestException("originalSaleItemId may only appear once per return");
            }

            SaleItem saleItem = sale.getItems().stream()
                    .filter(item -> item.getId().equals(saleItemId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Original sale item not found"));
            requireReturnable(saleItem);
            BigDecimal quantity = normalizeQuantity(itemRequest.quantity());
            BigDecimal alreadyReturned = returnedQuantity(saleItem.getId());
            BigDecimal requestedTotal = alreadyReturned.add(quantity);
            if (requestedTotal.compareTo(saleItem.getQuantity()) > 0) {
                throw new ConflictException("Return quantity cannot exceed purchased quantity");
            }

            String itemReason = requireReason(cleanOptional(itemRequest.reason()) == null ? returnReason : cleanOptional(itemRequest.reason()));
            returnRecord.addItem(returnItem(returnRecord, saleItem, quantity, itemReason, requestedTotal));
        }

        Return saved = save(returnRecord);
        ReturnResponse response = ReturnResponse.from(saved, isFullReturn(sale));
        audit(actor, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(UUID id) {
        Return returnRecord = returnRepository.findById(required(id, "return id"))
                .orElseThrow(() -> new NotFoundException("Return not found"));
        return ReturnResponse.from(returnRecord, isFullReturn(returnRecord.getOriginalSale()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> search(UUID originalSaleId, UUID storeId, int page, int size) {
        int pageNumber = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        var results = returnRepository.findAll(
                Specification.where(equalUuid("originalSale", "id", originalSaleId))
                        .and(equalUuid("store", "id", storeId)),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                results.getContent().stream().map(returnRecord -> ReturnResponse.from(returnRecord, isFullReturn(returnRecord.getOriginalSale()))).toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isFirst(),
                results.isLast());
    }

    private static Specification<Return> equalUuid(String association, String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(association).get(field), value);
    }

    private BigDecimal returnedQuantity(UUID saleItemId) {
        BigDecimal value = returnItemRepository.returnedQuantityForSaleItem(saleItemId);
        return value == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : value.setScale(QUANTITY_SCALE);
    }

    private ReturnItem returnItem(
            Return returnRecord,
            SaleItem saleItem,
            BigDecimal quantity,
            String reason,
            BigDecimal requestedTotalQuantity) {
        if (requestedTotalQuantity.compareTo(saleItem.getQuantity()) != 0) {
            return new ReturnItem(returnRecord, saleItem, quantity, reason);
        }

        BigDecimal remainingSubtotal = remainingAmount(
                saleItem.getLineSubtotal(),
                returnItemRepository.returnedSubtotalForSaleItem(saleItem.getId()));
        BigDecimal remainingTax = remainingAmount(
                saleItem.getEstimatedTaxAmount(),
                returnItemRepository.returnedTaxForSaleItem(saleItem.getId()));
        BigDecimal remainingTotal = remainingAmount(
                saleItem.getLineTotal(),
                returnItemRepository.returnedTotalForSaleItem(saleItem.getId()));
        return new ReturnItem(returnRecord, saleItem, quantity, reason, remainingSubtotal, remainingTax, remainingTotal);
    }

    private static BigDecimal remainingAmount(BigDecimal originalAmount, BigDecimal alreadyReturnedAmount) {
        BigDecimal returned = alreadyReturnedAmount == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : alreadyReturnedAmount.setScale(MONEY_SCALE);
        BigDecimal remaining = originalAmount.setScale(MONEY_SCALE).subtract(returned).setScale(MONEY_SCALE);
        if (remaining.signum() < 0) {
            throw new ConflictException("Returned amount cannot exceed purchased amount");
        }
        return remaining;
    }

    private boolean isFullReturn(Sale sale) {
        for (SaleItem saleItem : sale.getItems()) {
            BigDecimal totalReturned = returnedQuantity(saleItem.getId());
            if (totalReturned.compareTo(saleItem.getQuantity()) < 0) {
                return false;
            }
        }
        return !sale.getItems().isEmpty();
    }

    private static void requireCompletedSale(Sale sale) {
        if (sale.getStatus() != SaleStatus.COMPLETED
                && sale.getStatus() != SaleStatus.PARTIALLY_REFUNDED
                && sale.getStatus() != SaleStatus.REFUNDED) {
            throw new ConflictException("Returns can only be created for completed sales");
        }
    }

    private static void requireReturnable(SaleItem saleItem) {
        String capabilities = saleItem.getCompletedProductCapabilities();
        if (capabilities != null && !capabilities.isBlank()) {
            Set<String> snapshotCapabilities = Set.of(capabilities.split(","));
            if (!snapshotCapabilities.contains(ProductCapability.ALLOW_RETURN.name()) || snapshotCapabilities.contains(ProductCapability.NON_REFUNDABLE.name())) {
                throw new ConflictException("Product is not returnable");
            }
            return;
        }
        if (!saleItem.getProduct().hasCapability(ProductCapability.ALLOW_RETURN)
                || saleItem.getProduct().hasCapability(ProductCapability.NON_REFUNDABLE)) {
            throw new ConflictException("Product is not returnable");
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

    private Return save(Return returnRecord) {
        try {
            return returnRepository.saveAndFlush(returnRecord);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Return was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Return could not be saved");
        }
    }

    private void audit(User actor, ReturnResponse response) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.RETURN_CREATED,
                "RETURN",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                response.reason()));
    }

    private static UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private static String requireReason(String reason) {
        if (reason == null) {
            throw new BadRequestException("reason is required");
        }
        return reason;
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
}
