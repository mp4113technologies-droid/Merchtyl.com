package com.merchtyl.receipts;

import com.merchtyl.register.RegisterType;
import com.merchtyl.sales.SaleStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
class InvoiceHistoryQueryRepository {
    private static final String FROM_AND_FILTERS = """
            FROM receipts receipt
            JOIN sales sale ON sale.id = receipt.sale_id
            JOIN stores store ON store.id = sale.store_id
            JOIN registers register ON register.id = sale.register_id
            JOIN security_users cashier ON cashier.id = COALESCE(sale.completed_by, sale.created_by)
            WHERE store.tenant_id = :tenantId
              AND sale.status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
              AND (:allAuthorizedStores OR sale.store_id IN (:storeIds))
              AND (CAST(:storeId AS UUID) IS NULL OR sale.store_id = CAST(:storeId AS UUID))
              AND (:search = '' OR UPPER(receipt.receipt_number) LIKE UPPER(:search) || '%')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    InvoiceHistoryQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<InvoiceHistorySummary> find(
            UUID tenantId,
            Set<UUID> storeIds,
            boolean allAuthorizedStores,
            UUID storeId,
            String search,
            int page,
            int size) {
        MapSqlParameterSource parameters = parameters(
                tenantId, storeIds, allAuthorizedStores, storeId, search)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        return jdbc.query("""
                SELECT sale.id AS sale_id,
                       receipt.receipt_number,
                       sale.completed_at,
                       register.register_type,
                       store.id AS store_id,
                       store.name AS store_name,
                       register.id AS register_id,
                       register.code AS register_code,
                       cashier.display_name AS cashier_name,
                       sale.phone_customer_name AS customer_name,
                       sale.food_order_token AS order_number,
                       sale.total_amount,
                       sale.currency_code,
                       COALESCE((
                           SELECT CASE WHEN COUNT(DISTINCT payment.method) = 1 THEN MIN(payment.method)
                                       WHEN COUNT(DISTINCT payment.method) > 1 THEN 'SPLIT'
                                       ELSE NULL END
                           FROM payments payment
                           WHERE payment.sale_id = sale.id
                       ), 'OTHER') AS payment_summary,
                       sale.status
                """ + FROM_AND_FILTERS + """
                ORDER BY CASE WHEN :search <> '' AND UPPER(receipt.receipt_number) = UPPER(:search) THEN 0 ELSE 1 END,
                         sale.completed_at DESC,
                         sale.id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, (rs, rowNum) -> new InvoiceHistorySummary(
                rs.getObject("sale_id", UUID.class),
                rs.getString("receipt_number"),
                rs.getTimestamp("completed_at").toInstant(),
                RegisterType.valueOf(rs.getString("register_type")),
                rs.getObject("store_id", UUID.class),
                rs.getString("store_name"),
                rs.getObject("register_id", UUID.class),
                rs.getString("register_code"),
                rs.getString("cashier_name"),
                rs.getString("customer_name"),
                rs.getString("order_number"),
                rs.getBigDecimal("total_amount"),
                rs.getString("currency_code"),
                rs.getString("payment_summary"),
                SaleStatus.valueOf(rs.getString("status"))));
    }

    long count(
            UUID tenantId,
            Set<UUID> storeIds,
            boolean allAuthorizedStores,
            UUID storeId,
            String search) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) " + FROM_AND_FILTERS,
                parameters(tenantId, storeIds, allAuthorizedStores, storeId, search),
                Long.class);
        return count == null ? 0 : count;
    }

    private MapSqlParameterSource parameters(
            UUID tenantId,
            Set<UUID> storeIds,
            boolean allAuthorizedStores,
            UUID storeId,
            String search) {
        // Named-parameter expansion needs at least one value even when owners bypass this predicate.
        List<UUID> safeStoreIds = storeIds.isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(storeIds);
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("storeIds", safeStoreIds)
                .addValue("allAuthorizedStores", allAuthorizedStores)
                .addValue("storeId", storeId)
                .addValue("search", search);
    }
}
