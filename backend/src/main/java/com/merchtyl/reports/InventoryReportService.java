package com.merchtyl.reports;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.inventory.InventoryBalance;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.inventory.InventoryTransaction;
import com.merchtyl.inventory.InventoryTransactionRepository;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.product.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryReportService {
    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;
    private static final BigDecimal DEFAULT_LOW_STOCK_THRESHOLD = new BigDecimal("5.0000");
    private static final Set<InventoryTransactionType> ADJUSTMENT_TYPES = Set.of(
            InventoryTransactionType.ADJUSTMENT_INCREASE,
            InventoryTransactionType.ADJUSTMENT_DECREASE);
    private static final Set<InventoryTransactionType> DAMAGED_TYPES = Set.of(InventoryTransactionType.DAMAGED);
    private static final Set<InventoryTransactionType> EXPIRED_TYPES = Set.of(InventoryTransactionType.EXPIRED);

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public InventoryReportService(
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository) {
        this(balanceRepository, transactionRepository, Clock.systemUTC());
    }

    InventoryReportService(
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            Clock clock) {
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InventoryReportResponse summarize(InventoryReportRequest request) {
        InventoryReportRequest filters = normalize(request);
        List<InventoryStockReportRow> stockRows = balanceRepository
                .findAll(balanceSpecification(filters),
                        Sort.by(Sort.Direction.ASC, "store.name").and(Sort.by("product.name")).and(Sort.by("id")))
                .stream()
                .map(InventoryReportService::stockRow)
                .toList();
        List<InventoryStockReportRow> lowStockRows = stockRows.stream()
                .filter(row -> row.quantityOnHand().compareTo(BigDecimal.ZERO) >= 0)
                .filter(row -> row.quantityOnHand().compareTo(filters.lowStockThreshold()) <= 0)
                .toList();
        List<InventoryStockReportRow> negativeStockRows = stockRows.stream()
                .filter(row -> row.quantityOnHand().compareTo(BigDecimal.ZERO) < 0)
                .toList();

        List<InventoryActivityReportRow> adjustmentRows = activityRows(filters, ADJUSTMENT_TYPES);
        List<InventoryActivityReportRow> damagedRows = activityRows(filters, DAMAGED_TYPES);
        List<InventoryActivityReportRow> expiredRows = activityRows(filters, EXPIRED_TYPES);

        return new InventoryReportResponse(
                filters.storeId(),
                filters.categoryId(),
                filters.productId(),
                filters.dateFrom(),
                filters.dateTo(),
                filters.lowStockThreshold(),
                quantity(stockRows.stream()
                        .map(InventoryStockReportRow::quantityOnHand)
                        .reduce(quantityZero(), BigDecimal::add)),
                money(stockRows.stream()
                        .map(InventoryStockReportRow::inventoryValue)
                        .reduce(moneyZero(), BigDecimal::add)),
                stockRows.size(),
                lowStockRows.size(),
                negativeStockRows.size(),
                adjustmentRows.size(),
                damagedRows.size(),
                expiredRows.size(),
                totalQuantity(adjustmentRows),
                totalQuantity(damagedRows),
                totalQuantity(expiredRows),
                totalValue(adjustmentRows),
                totalValue(damagedRows),
                totalValue(expiredRows),
                stockRows,
                lowStockRows,
                negativeStockRows,
                adjustmentRows,
                damagedRows,
                expiredRows,
                Instant.now(clock));
    }

    private List<InventoryActivityReportRow> activityRows(InventoryReportRequest filters, Set<InventoryTransactionType> types) {
        return transactionRepository
                .findAll(
                        transactionSpecification(filters, types),
                        Sort.by(Sort.Direction.DESC, "occurredAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                                .and(Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(InventoryReportService::activityRow)
                .sorted(Comparator.comparing(InventoryActivityReportRow::occurredAt).reversed())
                .toList();
    }

    private static InventoryReportRequest normalize(InventoryReportRequest request) {
        if (request == null) {
            throw new BadRequestException("inventory report request is required");
        }
        if (request.dateFrom() != null && request.dateTo() != null && request.dateTo().isBefore(request.dateFrom())) {
            throw new BadRequestException("dateTo must be on or after dateFrom");
        }
        BigDecimal lowStockThreshold = request.lowStockThreshold() == null
                ? DEFAULT_LOW_STOCK_THRESHOLD
                : request.lowStockThreshold();
        if (lowStockThreshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("lowStockThreshold must not be negative");
        }
        return new InventoryReportRequest(
                request.storeId(),
                request.categoryId(),
                request.productId(),
                request.dateFrom(),
                request.dateTo(),
                quantity(lowStockThreshold));
    }

    private static InventoryStockReportRow stockRow(InventoryBalance balance) {
        Product product = balance.getProduct();
        UUID categoryId = product.getCategory() == null ? null : product.getCategory().getId();
        BigDecimal quantityOnHand = quantity(balance.getQuantityOnHand());
        BigDecimal cost = money(product.getCost());
        return new InventoryStockReportRow(
                balance.getStore().getId(),
                balance.getStore().getCode(),
                balance.getStore().getName(),
                product.getId(),
                product.getSku(),
                product.getName(),
                categoryId,
                cost,
                quantityOnHand,
                money(quantityOnHand.multiply(cost)),
                balance.getLastTransactionAt());
    }

    private static InventoryActivityReportRow activityRow(InventoryTransaction transaction) {
        Product product = transaction.getProduct();
        UUID categoryId = product.getCategory() == null ? null : product.getCategory().getId();
        BigDecimal absoluteQuantity = quantity(transaction.getQuantityDelta().abs());
        return new InventoryActivityReportRow(
                transaction.getId(),
                transaction.getStore().getId(),
                transaction.getStore().getCode(),
                transaction.getStore().getName(),
                product.getId(),
                product.getSku(),
                product.getName(),
                categoryId,
                transaction.getTransactionType(),
                quantity(transaction.getQuantityDelta()),
                absoluteQuantity,
                money(absoluteQuantity.multiply(money(product.getCost()))),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getReason(),
                transaction.getActorUserId(),
                transaction.getOccurredAt());
    }

    private static Specification<InventoryBalance> balanceSpecification(InventoryReportRequest request) {
        return Specification
                .where(balanceReference("store", request.storeId()))
                .and(balanceReference("product", request.productId()))
                .and(balanceProductCategory(request.categoryId()));
    }

    private static Specification<InventoryTransaction> transactionSpecification(
            InventoryReportRequest request,
            Set<InventoryTransactionType> types) {
        return Specification
                .where(transactionTypeIn(types))
                .and(transactionReference("store", request.storeId()))
                .and(transactionReference("product", request.productId()))
                .and(transactionProductCategory(request.categoryId()))
                .and(occurredAtGreaterThanOrEqualTo(request.dateFrom()))
                .and(occurredAtBeforeDayAfter(request.dateTo()));
    }

    private static Specification<InventoryBalance> balanceReference(String field, UUID value) {
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

    private static Specification<InventoryBalance> balanceProductCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("product").get("category").get("id"), categoryId);
    }

    private static Specification<InventoryTransaction> transactionProductCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("product").get("category").get("id"), categoryId);
    }

    private static Specification<InventoryTransaction> transactionTypeIn(Set<InventoryTransactionType> types) {
        return (root, query, criteriaBuilder) -> root.get("transactionType").in(types);
    }

    private static Specification<InventoryTransaction> occurredAtGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant start = value.atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), start);
    }

    private static Specification<InventoryTransaction> occurredAtBeforeDayAfter(LocalDate value) {
        if (value == null) {
            return null;
        }
        Instant exclusiveEnd = value.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("occurredAt"), exclusiveEnd);
    }

    private static BigDecimal totalQuantity(List<InventoryActivityReportRow> rows) {
        return quantity(rows.stream()
                .map(InventoryActivityReportRow::quantity)
                .reduce(quantityZero(), BigDecimal::add));
    }

    private static BigDecimal totalValue(List<InventoryActivityReportRow> rows) {
        return money(rows.stream()
                .map(InventoryActivityReportRow::inventoryValue)
                .reduce(moneyZero(), BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return moneyZero();
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null) {
            return quantityZero();
        }
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantityZero() {
        return BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }
}
