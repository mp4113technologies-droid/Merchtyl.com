package com.merchtyl.reports;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.refunds.Refund;
import com.merchtyl.refunds.RefundPayment;
import com.merchtyl.refunds.RefundRepository;
import com.merchtyl.returns.ReturnItem;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SalesReportService {
    private static final int MONEY_SCALE = 2;
    private static final Set<SaleStatus> REPORTABLE_SALE_STATUSES = Set.of(
            SaleStatus.COMPLETED,
            SaleStatus.PARTIALLY_REFUNDED,
            SaleStatus.REFUNDED);

    private final SaleRepository saleRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;

    @Autowired
    public SalesReportService(SaleRepository saleRepository, RefundRepository refundRepository) {
        this(saleRepository, refundRepository, Clock.systemUTC());
    }

    SalesReportService(SaleRepository saleRepository, RefundRepository refundRepository, Clock clock) {
        this.saleRepository = saleRepository;
        this.refundRepository = refundRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalesReportResponse summarize(SalesReportRequest request) {
        SalesReportRequest filters = normalize(request);
        List<Sale> sales = saleRepository.findAll(saleSpecification(filters));
        List<Refund> refunds = refundRepository.findAll(refundSpecification(filters));

        Totals totals = new Totals();
        Map<PaymentMethod, PaymentTotals> paymentTotals = new EnumMap<>(PaymentMethod.class);

        for (Sale sale : sales) {
            List<SaleItem> matchingItems = sale.getItems().stream()
                    .filter(item -> matchesProductFilters(item.getProduct().getId(),
                            item.getProduct().getCategory() == null ? null : item.getProduct().getCategory().getId(),
                            filters))
                    .toList();
            if (matchingItems.isEmpty()) {
                continue;
            }
            BigDecimal includedTotal = moneyZero();
            for (SaleItem item : matchingItems) {
                totals.grossSales = totals.grossSales.add(money(item.getLineSubtotal()));
                totals.discounts = totals.discounts.add(money(item.getDiscountAmount()));
                totals.saleTax = totals.saleTax.add(money(item.getEstimatedTaxAmount()));
                includedTotal = includedTotal.add(money(item.getLineTotal()));
            }
            BigDecimal ratio = ratio(includedTotal, sale.getTotalAmount());
            for (Payment payment : sale.getPayments()) {
                PaymentTotals methodTotals = paymentTotals.computeIfAbsent(payment.getMethod(), ignored -> new PaymentTotals());
                methodTotals.collected = methodTotals.collected.add(money(payment.getAmount()).multiply(ratio));
            }
        }

        long refundCount = 0;
        for (Refund refund : refunds) {
            List<ReturnItem> matchingItems = refund.getReturnRecord().getItems().stream()
                    .filter(item -> matchesProductFilters(item.getProduct().getId(),
                            item.getProduct().getCategory() == null ? null : item.getProduct().getCategory().getId(),
                            filters))
                    .toList();
            if (matchingItems.isEmpty()) {
                continue;
            }
            refundCount++;
            BigDecimal includedRefundTotal = moneyZero();
            for (ReturnItem item : matchingItems) {
                totals.refundSubtotal = totals.refundSubtotal.add(money(item.getReturnSubtotalAmount()));
                totals.refundTax = totals.refundTax.add(money(item.getReturnTaxAmount()));
                includedRefundTotal = includedRefundTotal.add(money(item.getReturnTotalAmount()));
            }
            BigDecimal ratio = ratio(includedRefundTotal, refund.getTotalAmount());
            for (RefundPayment payment : refund.getPayments()) {
                PaymentTotals methodTotals = paymentTotals.computeIfAbsent(payment.getMethod(), ignored -> new PaymentTotals());
                methodTotals.refunded = methodTotals.refunded.add(money(payment.getAmount()).multiply(ratio));
            }
        }

        BigDecimal refundsTotal = totals.refundSubtotal.add(totals.refundTax);
        BigDecimal netSales = totals.grossSales
                .subtract(totals.discounts)
                .subtract(totals.refundSubtotal);
        BigDecimal netTaxes = totals.saleTax.subtract(totals.refundTax);
        List<SalesReportPaymentBreakdown> breakdown = paymentTotals.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> new SalesReportPaymentBreakdown(
                        entry.getKey(),
                        money(entry.getValue().collected),
                        money(entry.getValue().refunded),
                        money(entry.getValue().collected.subtract(entry.getValue().refunded))))
                .toList();
        BigDecimal netPayments = breakdown.stream()
                .map(SalesReportPaymentBreakdown::net)
                .reduce(moneyZero(), BigDecimal::add);

        return new SalesReportResponse(
                filters.storeId(),
                filters.registerId(),
                filters.cashierId(),
                filters.categoryId(),
                filters.productId(),
                filters.dateFrom(),
                filters.dateTo(),
                money(totals.grossSales),
                money(netSales),
                money(totals.discounts),
                money(refundsTotal),
                money(netTaxes),
                money(netPayments),
                sales.size(),
                refundCount,
                breakdown,
                Instant.now(clock));
    }

    private static SalesReportRequest normalize(SalesReportRequest request) {
        if (request == null) {
            throw new BadRequestException("sales report request is required");
        }
        if (request.dateFrom() != null && request.dateTo() != null && request.dateTo().isBefore(request.dateFrom())) {
            throw new BadRequestException("dateTo must be on or after dateFrom");
        }
        return request;
    }

    private static boolean matchesProductFilters(UUID productId, UUID categoryId, SalesReportRequest request) {
        if (request.productId() != null && !request.productId().equals(productId)) {
            return false;
        }
        return request.categoryId() == null || request.categoryId().equals(categoryId);
    }

    private static BigDecimal ratio(BigDecimal includedTotal, BigDecimal fullTotal) {
        if (fullTotal == null || fullTotal.signum() == 0) {
            return BigDecimal.ONE.setScale(8);
        }
        return includedTotal.divide(fullTotal, 8, RoundingMode.HALF_UP);
    }

    private static Specification<Sale> saleSpecification(SalesReportRequest request) {
        return Specification
                .where(saleStatusIn(REPORTABLE_SALE_STATUSES))
                .and(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("completedBy", request.cashierId()))
                .and(businessDateGreaterThanOrEqualTo(request.dateFrom()))
                .and(businessDateLessThanOrEqualTo(request.dateTo()))
                .and(saleHasItemFilters(request.productId(), request.categoryId()));
    }

    private static Specification<Refund> refundSpecification(SalesReportRequest request) {
        return Specification
                .where(equalRefundReference("store", request.storeId()))
                .and(equalRefundReference("register", request.registerId()))
                .and(equalRefundReference("createdBy", request.cashierId()))
                .and(refundBusinessDateGreaterThanOrEqualTo(request.dateFrom()))
                .and(refundBusinessDateLessThanOrEqualTo(request.dateTo()))
                .and(refundHasItemFilters(request.productId(), request.categoryId()));
    }

    private static Specification<Sale> saleStatusIn(Set<SaleStatus> statuses) {
        return (root, query, criteriaBuilder) -> root.get("status").in(statuses);
    }

    private static Specification<Sale> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<Refund> equalRefundReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<Sale> businessDateGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<Sale> businessDateLessThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<Refund> refundBusinessDateGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<Refund> refundBusinessDateLessThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<Sale> saleHasItemFilters(UUID productId, UUID categoryId) {
        if (productId == null && categoryId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            var product = root.join("items").get("product");
            if (productId != null && categoryId != null) {
                return criteriaBuilder.and(
                        criteriaBuilder.equal(product.get("id"), productId),
                        criteriaBuilder.equal(product.get("category").get("id"), categoryId));
            }
            if (productId != null) {
                return criteriaBuilder.equal(product.get("id"), productId);
            }
            return criteriaBuilder.equal(product.get("category").get("id"), categoryId);
        };
    }

    private static Specification<Refund> refundHasItemFilters(UUID productId, UUID categoryId) {
        if (productId == null && categoryId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            var product = root.join("returnRecord").join("items").get("product");
            if (productId != null && categoryId != null) {
                return criteriaBuilder.and(
                        criteriaBuilder.equal(product.get("id"), productId),
                        criteriaBuilder.equal(product.get("category").get("id"), categoryId));
            }
            if (productId != null) {
                return criteriaBuilder.equal(product.get("id"), productId);
            }
            return criteriaBuilder.equal(product.get("category").get("id"), categoryId);
        };
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

    private static final class Totals {
        private BigDecimal grossSales = moneyZero();
        private BigDecimal discounts = moneyZero();
        private BigDecimal saleTax = moneyZero();
        private BigDecimal refundSubtotal = moneyZero();
        private BigDecimal refundTax = moneyZero();
    }

    private static final class PaymentTotals {
        private BigDecimal collected = moneyZero();
        private BigDecimal refunded = moneyZero();
    }
}
