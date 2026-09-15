package com.merchtyl.reports;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.receipts.ReceiptRepository;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.LotterySaleLineClassifier;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LotterySalesReportService {
    private final SaleRepository sales;
    private final ReceiptRepository receipts;
    private final StoreAccessService storeAccess;
    private final FeatureService features;
    private final Clock clock;

    public LotterySalesReportService(SaleRepository sales, ReceiptRepository receipts, StoreAccessService storeAccess,
                                     FeatureService features) {
        this(sales, receipts, storeAccess, features, Clock.systemUTC());
    }

    LotterySalesReportService(SaleRepository sales, ReceiptRepository receipts, StoreAccessService storeAccess,
                              FeatureService features, Clock clock) {
        this.sales = sales; this.receipts = receipts; this.storeAccess = storeAccess; this.features = features; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LotterySalesReportResponse summarize(LotterySalesReportRequest request, Authentication authentication) {
        validate(request);
        User actor = storeAccess.currentTenantUser(authentication);
        if (request.storeId() != null) {
            storeAccess.requireStoreAccess(authentication, request.storeId());
            features.requireEnabled(FeatureCode.LOTTERY_SALES, request.storeId(), request.registerId());
        }
        String type = normalizeFilter(request.type(), "ALL", "SOLD", "WIN");
        String source = normalizeFilter(request.source(), "ALL", "PHYSICAL_TICKET", "MANUAL");
        List<Sale> posted = sales.findAll(specification(request, actor), Sort.by("completedAt").descending().and(Sort.by("id")))
                .stream().filter(sale -> storeAccess.canAccessStore(actor.getId(), sale.getStore().getId())).toList();
        BigDecimal physical = zero(), manual = zero(), wins = zero();
        List<LotterySalesActivityRow> rows = new ArrayList<>();
        String currency = posted.stream().findFirst().map(Sale::getCurrencyCode).orElse("CAD");
        for (Sale sale : posted) {
            String receipt = receipts.findBySale_Id(sale.getId()).map(value -> value.getReceiptNumber()).orElse(null);
            for (SaleItem item : sale.getItems()) {
                boolean ticket = LotterySaleLineClassifier.isPhysicalTicket(item);
                boolean sold = LotterySaleLineClassifier.isManualSold(item);
                boolean win = LotterySaleLineClassifier.isWin(item);
                if (!ticket && !sold && !win) continue;
                String rowType = win ? "WIN" : "SOLD";
                String rowSource = ticket ? "PHYSICAL_TICKET" : "MANUAL";
                if (!"ALL".equals(type) && !type.equals(rowType)) continue;
                if (!"ALL".equals(source) && !source.equals(rowSource)) continue;
                BigDecimal amount = money(LotterySaleLineClassifier.reportingAmount(item));
                if (ticket) physical = physical.add(amount); else if (sold) manual = manual.add(amount); else wins = wins.add(amount);
                String description = ticket && item.getQuantity().compareTo(BigDecimal.ONE) != 0
                        ? item.getProductName() + " × " + item.getQuantity().stripTrailingZeros().toPlainString()
                        : item.getProductName();
                rows.add(new LotterySalesActivityRow(sale.getCompletedAt(),
                        sale.getRegister().getCode(), sale.getCreatedBy().getDisplayName(), rowType, rowSource,
                        description, amount, receipt));
            }
        }
        physical = money(physical); manual = money(manual); wins = money(wins);
        BigDecimal total = money(physical.add(manual));
        return new LotterySalesReportResponse(request.storeId(), request.registerId(), request.cashierId(),
                request.dateFrom(), request.dateTo(), type, source, physical, manual, total, wins,
                money(total.subtract(wins)), currency, List.copyOf(rows), Instant.now(clock));
    }

    private static Specification<Sale> specification(LotterySalesReportRequest request, User actor) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> values = new ArrayList<>();
            values.add(cb.equal(root.get("status"), SaleStatus.COMPLETED));
            values.add(cb.equal(root.get("store").get("tenantId"), actor.getTenantId()));
            if (request.storeId() != null) values.add(cb.equal(root.get("store").get("id"), request.storeId()));
            if (request.registerId() != null) values.add(cb.equal(root.get("register").get("id"), request.registerId()));
            if (request.cashierId() != null) values.add(cb.equal(root.get("createdBy").get("id"), request.cashierId()));
            if (request.dateFrom() != null) values.add(cb.greaterThanOrEqualTo(root.get("businessDate"), request.dateFrom()));
            if (request.dateTo() != null) values.add(cb.lessThanOrEqualTo(root.get("businessDate"), request.dateTo()));
            return cb.and(values.toArray(Predicate[]::new));
        };
    }

    private static void validate(LotterySalesReportRequest request) {
        if (request.dateFrom() != null && request.dateTo() != null && request.dateTo().isBefore(request.dateFrom()))
            throw new BadRequestException("dateTo must be on or after dateFrom");
    }
    private static String normalizeFilter(String value, String... allowed) {
        String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        for (String candidate : allowed) if (candidate.equals(normalized)) return normalized;
        throw new BadRequestException("Invalid Lottery report filter");
    }
    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(2); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
