-- Read-only lottery/till reconciliation for an authorized operator.
-- Run with psql variables, for example:
--   psql "$DATABASE_URL" \
--     -v tenant_id='00000000-0000-0000-0000-000000000000' \
--     -v store_id='00000000-0000-0000-0000-000000000000' \
--     -v business_date='2026-09-23' \
--     -f lottery_cash_reconciliation.sql
--
-- This script performs SELECTs only. It deliberately does not infer or insert
-- missing cash movements. Review every discrepancy against receipts and the
-- physical payout evidence before proposing a separately approved correction.

BEGIN TRANSACTION READ ONLY;

-- Active lottery catalog rows with a conflicting direct or assignment-based tax category.
SELECT p.id AS product_id, p.product_reference, p.sku, p.name, p.sellable_type,
       p.tax_category_id, tc.code AS tax_category_code, tc.treatment AS tax_treatment,
       assignment.id AS assignment_id, assigned.code AS assigned_tax_category_code,
       assigned.treatment AS assigned_tax_treatment
FROM products p
LEFT JOIN tax_categories tc ON tc.id = p.tax_category_id
LEFT JOIN product_tax_category_assignments assignment
  ON assignment.product_id = p.id AND assignment.active = TRUE
LEFT JOIN tax_categories assigned ON assigned.id = assignment.tax_category_id
WHERE p.tenant_id = :'tenant_id'::uuid
  AND p.active = TRUE
  AND p.deleted_at IS NULL
  AND p.sellable_type = 'LOTTERY_PRODUCT'
  AND (p.tax_category_id IS NOT NULL OR assignment.id IS NOT NULL)
ORDER BY p.created_at, p.id;

-- Current catalog mismatches. These rows require an explicit, audited catalog
-- correction; this diagnostic intentionally performs no UPDATE.
SELECT p.id AS product_id, p.product_reference, p.sku, p.name,
       p.sellable_type, c.id AS category_id, c.code AS category_code,
       c.system_type, p.active, p.created_at, p.updated_at
FROM products p
JOIN categories c ON c.id = p.category_id
WHERE p.tenant_id = :'tenant_id'::uuid
  AND c.tenant_id = p.tenant_id
  AND c.system_managed = TRUE
  AND c.system_type = 'LOTTERY'
  AND p.sellable_type <> 'LOTTERY_PRODUCT'
ORDER BY p.created_at, p.id;

-- Completed historical lines sold from a system Lottery category while their
-- immutable transaction snapshot was not semantically classified as Lottery.
-- This is the affected-transaction report, not permission to rewrite snapshots
-- or ledger entries.
SELECT s.id AS sale_id, r.receipt_number, s.store_id, s.register_id,
       s.register_session_id, s.business_date, s.completed_at,
       si.id AS sale_item_id, si.product_id, si.variant_id,
       si.product_name, si.sellable_type_snapshot, si.quantity,
       si.line_total, c.id AS category_id, c.code AS category_code
FROM sales s
JOIN stores st ON st.id = s.store_id
JOIN sale_items si ON si.sale_id = s.id
JOIN products p ON p.id = si.product_id
JOIN categories c ON c.id = p.category_id
LEFT JOIN receipts r ON r.sale_id = s.id
WHERE st.tenant_id = :'tenant_id'::uuid
  AND s.store_id = :'store_id'::uuid
  AND s.business_date = :'business_date'::date
  AND s.status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
  AND c.system_managed = TRUE
  AND c.system_type = 'LOTTERY'
  AND si.sellable_type_snapshot IS DISTINCT FROM 'LOTTERY_PRODUCT'
ORDER BY s.completed_at, s.id, si.line_number;

-- Historical completed lines that were actually charged tax while carrying the Lottery semantic.
-- This is a review list only; never update these snapshots in place.
SELECT s.id AS sale_id, r.receipt_number, s.business_date, s.completed_at,
       si.id AS sale_item_id, si.product_id, si.product_name,
       si.quantity, si.line_subtotal, si.estimated_tax_amount, si.line_total
FROM sales s
JOIN stores st ON st.id = s.store_id
JOIN sale_items si ON si.sale_id = s.id
LEFT JOIN receipts r ON r.sale_id = s.id
WHERE st.tenant_id = :'tenant_id'::uuid
  AND s.store_id = :'store_id'::uuid
  AND s.business_date = :'business_date'::date
  AND s.status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
  AND si.sellable_type_snapshot = 'LOTTERY_PRODUCT'
  AND si.estimated_tax_amount <> 0
ORDER BY s.completed_at, s.id, si.line_number;

WITH scoped_sessions AS (
    SELECT rs.id, rs.register_id, rs.opening_cash, rs.counted_cash,
           rs.expected_cash_at_close, rs.difference_cash
    FROM register_sessions rs
    JOIN stores st ON st.id = rs.store_id
    JOIN business_days bd ON bd.id = rs.business_day_id
    WHERE st.tenant_id = :'tenant_id'::uuid
      AND rs.store_id = :'store_id'::uuid
      AND bd.business_date = :'business_date'::date
), ledger AS (
    SELECT cle.register_session_id,
           COALESCE(SUM(cle.amount) FILTER (WHERE cle.source_type = 'SALE_CASH_RECEIPT' AND cle.direction = 'IN'), 0) AS cash_received,
           COALESCE(SUM(cle.amount) FILTER (WHERE cle.source_type = 'LOTTERY_SALE_CASH' AND cle.direction = 'IN'), 0) AS lottery_cash_sales,
           COALESCE(SUM(cle.amount) FILTER (WHERE cle.source_type = 'SALE_CHANGE_GIVEN' AND cle.direction = 'OUT'), 0) AS change_given,
           COALESCE(SUM(cle.amount) FILTER (WHERE cle.source_type = 'LOTTERY_PAYOUT_CASH' AND cle.direction = 'OUT'), 0) AS lottery_cash_payouts,
           COALESCE(SUM(cle.amount) FILTER (WHERE cle.source_type = 'CASH_REFUND' AND cle.direction = 'OUT'), 0) AS cash_refunds,
           COALESCE(SUM(cle.amount) FILTER (WHERE cm.type IN ('CASH_IN', 'FLOAT_ADD') AND cle.direction = 'IN'), 0) AS cash_additions,
           COALESCE(SUM(cle.amount) FILTER (WHERE cm.type IN ('SAFE_DROP', 'BANK_DEPOSIT') AND cle.direction = 'OUT'), 0) AS cash_drops,
           COALESCE(SUM(CASE WHEN cle.direction = 'IN' THEN cle.amount ELSE -cle.amount END)
                    FILTER (WHERE cle.source_type <> 'SESSION_OPENING_FLOAT'), 0) AS net_after_opening
    FROM cash_ledger_entries cle
    JOIN scoped_sessions ss ON ss.id = cle.register_session_id
    LEFT JOIN cash_movements cm
      ON cle.source_type = 'CASH_MOVEMENT' AND cm.id = cle.source_id
    GROUP BY cle.register_session_id
)
SELECT ss.id AS register_session_id, r.code AS register_code,
       ss.opening_cash,
       COALESCE(l.cash_received, 0) AS cash_received,
       COALESCE(l.lottery_cash_sales, 0) AS lottery_cash_sales,
       COALESCE(l.change_given, 0) AS change_given,
       COALESCE(l.lottery_cash_payouts, 0) AS lottery_cash_payouts,
       COALESCE(l.cash_refunds, 0) AS cash_refunds,
       COALESCE(l.cash_additions, 0) AS cash_additions,
       COALESCE(l.cash_drops, 0) AS cash_drops,
       ss.opening_cash + COALESCE(l.net_after_opening, 0) AS recalculated_expected_cash,
       ss.expected_cash_at_close AS stored_expected_cash,
       ss.counted_cash AS counted_closing_cash,
       ss.counted_cash - (ss.opening_cash + COALESCE(l.net_after_opening, 0)) AS recalculated_variance,
       ss.difference_cash AS stored_variance
FROM scoped_sessions ss
JOIN registers r ON r.id = ss.register_id
LEFT JOIN ledger l ON l.register_session_id = ss.id
ORDER BY r.code, ss.id;

-- Transaction-level completed Lottery classification, tender and immutable cash
-- movements. A win can legitimately have no payout entry when offset against a
-- purchase or settled non-cash. Ledger inclusion is identical for Till and EOD.
WITH lottery_lines AS (
    SELECT sale_id,
           SUM(ABS(line_total)) FILTER (WHERE sellable_type_snapshot = 'LOTTERY_PRODUCT') AS physical_lottery_sold,
           SUM(ABS(line_total)) FILTER (WHERE line_type = 'LOTTERY_SOLD') AS manual_lottery_sold,
           SUM(ABS(line_total)) FILTER (WHERE line_type = 'LOTTERY_WIN') AS lottery_wins
    FROM sale_items
    WHERE sellable_type_snapshot = 'LOTTERY_PRODUCT'
       OR line_type IN ('LOTTERY_SOLD', 'LOTTERY_WIN')
    GROUP BY sale_id
), payment_totals AS (
    SELECT sale_id,
           STRING_AGG(method, ',' ORDER BY created_at) AS payment_methods,
           SUM(cash_tendered) FILTER (WHERE method = 'CASH') AS cash_tendered,
           SUM(cash_settlement_amount) FILTER (WHERE method = 'CASH') AS cash_applied,
           SUM(change_due) FILTER (WHERE method = 'CASH') AS change_given
    FROM payments
    GROUP BY sale_id
), scoped_sales AS (
    SELECT s.id, s.register_session_id, s.register_id, s.completed_at,
           s.business_date, s.total_amount, r.receipt_number,
           COALESCE(lines.physical_lottery_sold, 0) AS physical_lottery_sold,
           COALESCE(lines.manual_lottery_sold, 0) AS manual_lottery_sold,
           COALESCE(lines.lottery_wins, 0) AS lottery_wins,
           payments.payment_methods,
           COALESCE(payments.cash_tendered, 0) AS cash_tendered,
           COALESCE(payments.cash_applied, 0) AS cash_applied,
           COALESCE(payments.change_given, 0) AS change_given
    FROM sales s
    JOIN stores st ON st.id = s.store_id
    JOIN lottery_lines lines ON lines.sale_id = s.id
    LEFT JOIN payment_totals payments ON payments.sale_id = s.id
    LEFT JOIN receipts r ON r.sale_id = s.id
    WHERE st.tenant_id = :'tenant_id'::uuid
      AND s.store_id = :'store_id'::uuid
      AND s.business_date = :'business_date'::date
      AND s.status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
)
SELECT ss.receipt_number, ss.id AS sale_id, ss.register_session_id,
       reg.code AS register_code, ss.business_date, ss.completed_at,
       ss.physical_lottery_sold, ss.manual_lottery_sold, ss.lottery_wins,
       ss.payment_methods, ss.cash_tendered, ss.cash_applied, ss.change_given,
       COALESCE(ledger.lottery_cash_sale, 0) AS classified_lottery_cash_sale,
       COALESCE(ledger.generic_sale_cash_receipt, 0) AS generic_sale_cash_receipt,
       COALESCE(ledger.actual_lottery_cash_payout, 0) AS actual_lottery_cash_payout,
       ledger.cash_movement_ids,
       COALESCE(ledger.net_till_amount, 0) AS amount_included_in_till_report,
       COALESCE(ledger.net_till_amount, 0) AS amount_included_in_eod,
       ss.total_amount,
       CASE
         WHEN ss.total_amount < 0 AND COALESCE(ledger.actual_lottery_cash_payout, 0) = 0
           THEN 'NEGATIVE_SALE_MISSING_PAYOUT_LEDGER'
         WHEN ss.total_amount >= 0 AND COALESCE(ledger.actual_lottery_cash_payout, 0) <> 0
           THEN 'REVIEW_UNEXPECTED_PAYOUT_LEDGER'
         WHEN (ss.physical_lottery_sold + ss.manual_lottery_sold) > 0
              AND COALESCE(ledger.lottery_cash_sale, 0) = 0
              AND COALESCE(ledger.generic_sale_cash_receipt, 0) > 0
           THEN 'LEGACY_LOTTERY_CASH_CLASSIFIED_AS_GENERIC_SALE_RECEIPT'
         ELSE 'OK'
       END AS reconciliation_status
FROM scoped_sales ss
JOIN registers reg ON reg.id = ss.register_id
LEFT JOIN LATERAL (
    SELECT STRING_AGG(cle.id::text, ',' ORDER BY cle.occurred_at) AS cash_movement_ids,
           SUM(cle.amount) FILTER (WHERE cle.source_type = 'LOTTERY_SALE_CASH' AND cle.direction = 'IN') AS lottery_cash_sale,
           SUM(cle.amount) FILTER (WHERE cle.source_type = 'SALE_CASH_RECEIPT' AND cle.direction = 'IN') AS generic_sale_cash_receipt,
           SUM(cle.amount) FILTER (WHERE cle.source_type = 'LOTTERY_PAYOUT_CASH' AND cle.direction = 'OUT') AS actual_lottery_cash_payout,
           SUM(CASE WHEN cle.direction = 'IN' THEN cle.amount ELSE -cle.amount END) AS net_till_amount
    FROM cash_ledger_entries cle
    WHERE cle.register_session_id = ss.register_session_id
      AND (cle.source_id = ss.id OR cle.source_id IN (SELECT p.id FROM payments p WHERE p.sale_id = ss.id))
) ledger ON TRUE
ORDER BY ss.completed_at, ss.id;

-- Standalone lottery payouts and their immutable cash-ledger records.
SELECT lp.id AS lottery_payout_id, lp.ticket_number, lp.register_session_id,
       r.code AS register_code, lp.occurred_at, lp.amount AS win_amount,
       lp.payout_method, lp.status, cle.amount AS actual_cash_payout,
       CASE
         WHEN lp.status = 'PAID' AND lp.payout_method = 'CASH' AND cle.id IS NULL
           THEN 'PAID_CASH_PAYOUT_MISSING_LEDGER'
         WHEN cle.id IS NOT NULL AND (lp.status <> 'PAID' OR lp.payout_method <> 'CASH')
           THEN 'REVIEW_UNEXPECTED_PAYOUT_LEDGER'
         ELSE 'OK'
       END AS reconciliation_status
FROM lottery_payouts lp
JOIN stores st ON st.id = lp.store_id
JOIN registers r ON r.id = lp.register_id
LEFT JOIN cash_ledger_entries cle
  ON cle.source_type = 'LOTTERY_PAYOUT_CASH'
 AND cle.direction = 'OUT'
 AND cle.source_id = lp.id
 AND cle.register_session_id = lp.register_session_id
WHERE st.tenant_id = :'tenant_id'::uuid
  AND lp.store_id = :'store_id'::uuid
  AND lp.business_date = :'business_date'::date
ORDER BY lp.occurred_at, lp.id;

ROLLBACK;
