package com.merchtyl.sales;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Aggregates immutable completed-line snapshots without loading Sale graphs. */
@Service
public class SalesClassificationService {
    private static final String REPORTABLE_STATUSES = "'COMPLETED','PARTIALLY_REFUNDED','REFUNDED'";
    private final NamedParameterJdbcTemplate jdbc;

    public SalesClassificationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<UUID, SalesClassification> byRegisterSessions(UUID tenantId, Collection<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return Map.of();
        Map<UUID, MutableTotals> totals = new LinkedHashMap<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("tenantId", tenantId)
                .addValue("sessionIds", sessionIds);
        jdbc.query(saleSql(), parameters, (org.springframework.jdbc.core.RowCallbackHandler) row -> accumulate(totals, row.getObject("session_id", UUID.class),
                row.getBigDecimal("taxable_sales"), row.getBigDecimal("non_taxable_sales"), row.getBigDecimal("tax_collected")));
        jdbc.query(refundSql(), parameters, (org.springframework.jdbc.core.RowCallbackHandler) row -> accumulate(totals, row.getObject("session_id", UUID.class),
                row.getBigDecimal("taxable_sales").negate(), row.getBigDecimal("non_taxable_sales").negate(),
                row.getBigDecimal("tax_collected").negate()));
        Map<UUID, SalesClassification> result = new LinkedHashMap<>();
        sessionIds.forEach(id -> result.put(id, totals.getOrDefault(id, new MutableTotals()).snapshot()));
        return result;
    }

    public SalesClassification forRegisterSession(UUID tenantId, UUID sessionId) {
        return byRegisterSessions(tenantId, java.util.List.of(sessionId)).getOrDefault(sessionId, SalesClassification.zero());
    }

    private static String saleSql() {
        return ("""
                SELECT s.register_session_id AS session_id,
                  COALESCE(SUM(CASE WHEN %s = 'TAXABLE' THEN %s ELSE 0 END), 0) AS taxable_sales,
                  COALESCE(SUM(CASE WHEN %s = 'NON_TAXABLE' THEN %s ELSE 0 END), 0) AS non_taxable_sales,
                  COALESCE(SUM(CASE WHEN %s THEN si.estimated_tax_amount ELSE 0 END), 0) AS tax_collected
                FROM sales s
                JOIN stores st ON st.id = s.store_id
                JOIN sale_items si ON si.sale_id = s.id
                WHERE st.tenant_id = :tenantId AND s.register_session_id IN (:sessionIds)
                  AND s.status IN (""" + REPORTABLE_STATUSES + ") AND " + eligible() + " GROUP BY s.register_session_id")
                .formatted(treatment(), netSale(), treatment(), netSale(), eligible());
    }

    private static String refundSql() {
        String netReturn = "(ri.return_subtotal_amount - COALESCE(ri.return_deposit_total, 0) "
                + "- (COALESCE(ri.original_discount_amount, 0) * ri.quantity / NULLIF(ri.original_quantity, 0)))";
        return ("""
                SELECT f.register_session_id AS session_id,
                  COALESCE(SUM(CASE WHEN %s = 'TAXABLE' THEN %s ELSE 0 END), 0) AS taxable_sales,
                  COALESCE(SUM(CASE WHEN %s = 'NON_TAXABLE' THEN %s ELSE 0 END), 0) AS non_taxable_sales,
                  COALESCE(SUM(CASE WHEN %s THEN ri.return_tax_amount ELSE 0 END), 0) AS tax_collected
                FROM refunds f
                JOIN stores st ON st.id = f.store_id
                JOIN returns r ON r.id = f.return_id
                JOIN return_items ri ON ri.return_id = r.id
                JOIN sale_items si ON si.id = ri.original_sale_item_id
                WHERE st.tenant_id = :tenantId AND f.register_session_id IN (:sessionIds)
                """ + " AND " + eligible() + " GROUP BY f.register_session_id")
                .formatted(treatment(), netReturn, treatment(), netReturn, eligible());
    }

    private static String eligible() {
        return "si.line_type IN ('CATALOG_PRODUCT','CUSTOM_ITEM') "
                + "AND COALESCE(si.sellable_type_snapshot, 'STANDARD_PRODUCT') <> 'LOTTERY_PRODUCT'";
    }

    private static String treatment() {
        // Legacy rows remain untouched. Their actual charged tax is the only historical signal available.
        return "COALESCE(si.historical_tax_treatment, CASE "
                + "WHEN si.custom_item_tax_treatment = 'TAXABLE' THEN 'TAXABLE' "
                + "WHEN si.custom_item_tax_treatment = 'NON_TAXABLE' THEN 'NON_TAXABLE' "
                + "WHEN si.estimated_tax_amount <> 0 THEN 'TAXABLE' ELSE 'NON_TAXABLE' END)";
    }

    private static String netSale() {
        return "(si.line_subtotal - COALESCE(si.deposit_total, 0) - si.discount_amount)";
    }

    private static void accumulate(Map<UUID, MutableTotals> totals, UUID id, BigDecimal taxable,
                                   BigDecimal nonTaxable, BigDecimal tax) {
        MutableTotals value = totals.computeIfAbsent(id, ignored -> new MutableTotals());
        value.taxable = value.taxable.add(taxable);
        value.nonTaxable = value.nonTaxable.add(nonTaxable);
        value.tax = value.tax.add(tax);
    }

    private static final class MutableTotals {
        private BigDecimal taxable = BigDecimal.ZERO;
        private BigDecimal nonTaxable = BigDecimal.ZERO;
        private BigDecimal tax = BigDecimal.ZERO;
        private SalesClassification snapshot() {
            BigDecimal taxableMoney = money(taxable);
            BigDecimal nonTaxableMoney = money(nonTaxable);
            return new SalesClassification(taxableMoney, nonTaxableMoney, money(tax), money(taxableMoney.add(nonTaxableMoney)));
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
