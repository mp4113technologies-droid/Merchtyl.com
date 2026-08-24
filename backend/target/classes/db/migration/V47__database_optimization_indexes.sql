CREATE INDEX IF NOT EXISTS idx_register_sessions_status_opened_at
    ON register_sessions (status, opened_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_register_sessions_register_status_opened_at
    ON register_sessions (register_id, status, opened_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_register_sessions_device_status_opened_at
    ON register_sessions (device_id, status, opened_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_register_sessions_cashier_status_opened_at
    ON register_sessions (assigned_cashier_id, status, opened_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cash_ledger_entries_session_occurred_created
    ON cash_ledger_entries (register_session_id, occurred_at ASC, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_audit_records_search_created_id
    ON audit_records (created_at DESC, id DESC, action, entity_type, actor_user_id, store_id, register_id);

CREATE INDEX IF NOT EXISTS idx_sales_search_updated_id
    ON sales (store_id, register_id, register_session_id, created_by, status, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_sales_updated_id
    ON sales (updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_sales_report_filters
    ON sales (store_id, business_date, status, register_id, completed_by, id);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale_product
    ON sale_items (sale_id, product_id, line_number);

CREATE INDEX IF NOT EXISTS idx_payments_sale_completed
    ON payments (sale_id, completed_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_returns_search_occurred_id
    ON returns (store_id, original_sale_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_return_items_product
    ON return_items (product_id);

CREATE INDEX IF NOT EXISTS idx_refunds_report_filters
    ON refunds (store_id, business_date, register_id, created_by, id);

CREATE INDEX IF NOT EXISTS idx_refunds_search_occurred_id
    ON refunds (store_id, register_session_id, original_sale_id, return_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_refund_payments_refund_line
    ON refund_payments (refund_id, line_number ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_inventory_transactions_report_filters
    ON inventory_transactions (store_id, transaction_type, occurred_at DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_transactions_product_type_occurred
    ON inventory_transactions (product_id, transaction_type, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_balances_report_sort
    ON inventory_balances (store_id, product_id, last_transaction_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cash_movements_search_occurred_id
    ON cash_movements (store_id, register_id, register_session_id, type, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_sales_report_filters
    ON lottery_sales (operator_id, store_id, register_id, cashier_id, occurred_at DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_sales_history_search
    ON lottery_sales (operator_id, store_id, status, payment_method, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_payouts_report_filters
    ON lottery_payouts (operator_id, store_id, register_id, cashier_id, occurred_at DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_payouts_search_created_id
    ON lottery_payouts (operator_id, store_id, register_id, register_session_id, status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_payouts_reserved_cash
    ON lottery_payouts (register_session_id, payout_method, status, id)
    WHERE payout_method = 'CASH' AND status = 'AUTHORIZED';

CREATE INDEX IF NOT EXISTS idx_lottery_payout_approvals_report_filters
    ON lottery_payout_approvals (approved_at DESC, created_at DESC, payout_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_sale_cancellations_report_filters
    ON lottery_sale_cancellations (cancelled_at DESC, created_at DESC, original_sale_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_payout_reversals_report_filters
    ON lottery_payout_reversals (reversed_at DESC, created_at DESC, original_payout_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_lottery_settlements_report_filters
    ON lottery_settlements (operator_id, store_id, period_end DESC, period_start, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_users_email_lower
    ON security_users (lower(email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_stores_code_lower
    ON stores (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_registers_store_code_lower
    ON registers (store_id, lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_devices_identifier_lower
    ON devices (lower(device_identifier));

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_code_lower
    ON categories (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_brands_code_lower
    ON brands (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_units_of_measure_code_lower
    ON units_of_measure (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_code_lower
    ON suppliers (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_countries_code_lower
    ON countries (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_administrative_areas_country_code_lower
    ON administrative_areas (country_id, lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_jurisdictions_country_code_lower
    ON tax_jurisdictions (country_id, lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_types_code_lower
    ON tax_types (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_components_code_lower
    ON tax_components (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_groups_code_lower
    ON tax_groups (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_categories_code_lower
    ON tax_categories (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_rules_code_lower
    ON tax_rules (lower(code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_products_sku_lower
    ON products (lower(sku));

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_variants_sku_lower
    ON product_variants (lower(sku));

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_barcodes_barcode_lower
    ON product_barcodes (lower(barcode));

CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_payments_refund_original_payment
    ON refund_payments (refund_id, original_payment_id)
    WHERE original_payment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lottery_operators_code_lower
    ON lottery_operators (lower(code));

ALTER TABLE sales DROP CONSTRAINT IF EXISTS ck_sales_completed_timestamp;

ALTER TABLE sales ADD CONSTRAINT ck_sales_completed_timestamp CHECK (
    (
        status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
        AND completed_by IS NOT NULL
        AND completed_at IS NOT NULL
    )
    OR status NOT IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED')
);
