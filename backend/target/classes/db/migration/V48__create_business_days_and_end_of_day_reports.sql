CREATE TABLE business_days (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    business_date DATE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    opened_by UUID NOT NULL,
    closing_started_at TIMESTAMPTZ,
    closing_started_by UUID,
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    reopen_reason VARCHAR(1000),
    force_close_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_business_days_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_business_days_opened_by FOREIGN KEY (opened_by) REFERENCES security_users(id),
    CONSTRAINT fk_business_days_closing_started_by FOREIGN KEY (closing_started_by) REFERENCES security_users(id),
    CONSTRAINT fk_business_days_closed_by FOREIGN KEY (closed_by) REFERENCES security_users(id),
    CONSTRAINT uq_business_days_store_date UNIQUE (store_id, business_date),
    CONSTRAINT ck_business_days_status CHECK (status IN ('OPEN', 'CLOSING', 'CLOSED', 'REOPENED')),
    CONSTRAINT ck_business_days_closing_actor CHECK (
        (closing_started_at IS NULL AND closing_started_by IS NULL)
        OR (closing_started_at IS NOT NULL AND closing_started_by IS NOT NULL)
    ),
    CONSTRAINT ck_business_days_closed_actor CHECK (
        (closed_at IS NULL AND closed_by IS NULL)
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_business_days_store_active
    ON business_days (store_id)
    WHERE status IN ('OPEN', 'CLOSING', 'REOPENED');

CREATE INDEX idx_business_days_store_date_status
    ON business_days (store_id, business_date DESC, status, id DESC);

CREATE INDEX idx_business_days_status_closed
    ON business_days (status, closed_at DESC, id DESC);

CREATE TABLE end_of_day_reports (
    id UUID PRIMARY KEY,
    business_day_id UUID NOT NULL,
    store_id UUID NOT NULL,
    business_date DATE NOT NULL,
    report_number VARCHAR(80) NOT NULL,
    revision INTEGER NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by UUID NOT NULL,
    gross_sales NUMERIC(12,2) NOT NULL,
    net_sales NUMERIC(12,2) NOT NULL,
    discount_total NUMERIC(12,2) NOT NULL,
    refund_total NUMERIC(12,2) NOT NULL,
    void_total NUMERIC(12,2) NOT NULL,
    tax_total NUMERIC(12,2) NOT NULL,
    transaction_count BIGINT NOT NULL,
    average_transaction_value NUMERIC(12,2) NOT NULL,
    highest_transaction_value NUMERIC(12,2) NOT NULL,
    lowest_transaction_value NUMERIC(12,2) NOT NULL,
    items_sold NUMERIC(19,4) NOT NULL,
    average_basket_size NUMERIC(19,4) NOT NULL,
    expected_cash NUMERIC(12,2) NOT NULL,
    counted_cash NUMERIC(12,2) NOT NULL,
    cash_variance NUMERIC(12,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    report_snapshot TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_reports_business_day FOREIGN KEY (business_day_id) REFERENCES business_days(id),
    CONSTRAINT fk_eod_reports_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_eod_reports_generated_by FOREIGN KEY (generated_by) REFERENCES security_users(id),
    CONSTRAINT uq_eod_reports_store_number UNIQUE (store_id, report_number),
    CONSTRAINT uq_eod_reports_business_day_revision UNIQUE (business_day_id, revision)
);

CREATE INDEX idx_eod_reports_store_date
    ON end_of_day_reports (store_id, business_date DESC, id DESC);

CREATE INDEX idx_eod_reports_report_number
    ON end_of_day_reports (report_number);

CREATE INDEX idx_eod_reports_generated_closed
    ON end_of_day_reports (generated_at DESC, id DESC);

CREATE TABLE end_of_day_register_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    register_session_id UUID,
    register_id UUID NOT NULL,
    register_code VARCHAR(64) NOT NULL,
    register_name VARCHAR(180) NOT NULL,
    opening_float NUMERIC(12,2) NOT NULL,
    cash_receipts NUMERIC(12,2) NOT NULL,
    change_given NUMERIC(12,2) NOT NULL,
    cash_refunds NUMERIC(12,2) NOT NULL,
    lottery_cash_sales NUMERIC(12,2) NOT NULL,
    lottery_payouts NUMERIC(12,2) NOT NULL,
    lottery_payout_reversals NUMERIC(12,2) NOT NULL,
    lottery_sale_cancellations NUMERIC(12,2) NOT NULL,
    cash_in NUMERIC(12,2) NOT NULL,
    cash_out NUMERIC(12,2) NOT NULL,
    safe_drops NUMERIC(12,2) NOT NULL,
    float_additions NUMERIC(12,2) NOT NULL,
    float_removals NUMERIC(12,2) NOT NULL,
    expenses NUMERIC(12,2) NOT NULL,
    closing_adjustments NUMERIC(12,2) NOT NULL,
    expected_cash NUMERIC(12,2) NOT NULL,
    counted_cash NUMERIC(12,2) NOT NULL,
    variance NUMERIC(12,2) NOT NULL,
    opened_by UUID NOT NULL,
    opened_by_name VARCHAR(180) NOT NULL,
    closed_by UUID,
    closed_by_name VARCHAR(180),
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    force_closed BOOLEAN NOT NULL,
    force_close_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_register_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id),
    CONSTRAINT fk_eod_register_summaries_session FOREIGN KEY (register_session_id) REFERENCES register_sessions(id),
    CONSTRAINT fk_eod_register_summaries_register FOREIGN KEY (register_id) REFERENCES registers(id),
    CONSTRAINT fk_eod_register_summaries_opened_by FOREIGN KEY (opened_by) REFERENCES security_users(id),
    CONSTRAINT fk_eod_register_summaries_closed_by FOREIGN KEY (closed_by) REFERENCES security_users(id)
);

CREATE INDEX idx_eod_register_summaries_report
    ON end_of_day_register_summaries (report_id, register_code);

CREATE TABLE end_of_day_payment_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    collected NUMERIC(12,2) NOT NULL,
    refunded NUMERIC(12,2) NOT NULL,
    net NUMERIC(12,2) NOT NULL,
    cash_tendered NUMERIC(12,2) NOT NULL,
    change_given NUMERIC(12,2) NOT NULL,
    transaction_count BIGINT NOT NULL,
    split_payment_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_payment_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id),
    CONSTRAINT uq_eod_payment_summaries_method UNIQUE (report_id, payment_method)
);

CREATE TABLE end_of_day_tax_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    component_code VARCHAR(80) NOT NULL,
    component_name VARCHAR(180) NOT NULL,
    taxable_sales NUMERIC(12,2) NOT NULL,
    exempt_sales NUMERIC(12,2) NOT NULL,
    zero_rated_sales NUMERIC(12,2) NOT NULL,
    out_of_scope_sales NUMERIC(12,2) NOT NULL,
    tax_collected NUMERIC(12,2) NOT NULL,
    tax_refunded NUMERIC(12,2) NOT NULL,
    rounding_adjustment NUMERIC(12,2) NOT NULL,
    net_tax_collected NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_tax_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id),
    CONSTRAINT uq_eod_tax_summaries_component UNIQUE (report_id, component_code)
);

CREATE TABLE end_of_day_lottery_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL,
    lottery_sales NUMERIC(12,2) NOT NULL,
    lottery_payouts NUMERIC(12,2) NOT NULL,
    sale_cancellations NUMERIC(12,2) NOT NULL,
    payout_reversals NUMERIC(12,2) NOT NULL,
    cash_lottery_activity NUMERIC(12,2) NOT NULL,
    non_cash_lottery_activity NUMERIC(12,2) NOT NULL,
    commission_earned NUMERIC(12,2) NOT NULL,
    settlement_amount NUMERIC(12,2) NOT NULL,
    operator_referrals BIGINT NOT NULL,
    pending_referrals BIGINT NOT NULL,
    approval_count BIGINT NOT NULL,
    rejected_payouts BIGINT NOT NULL,
    operator_totals TEXT NOT NULL,
    register_totals TEXT NOT NULL,
    cashier_totals TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_lottery_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id)
);

CREATE TABLE end_of_day_inventory_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE,
    deducted_by_sales NUMERIC(19,4) NOT NULL,
    restored_by_returns NUMERIC(19,4) NOT NULL,
    manual_increases NUMERIC(19,4) NOT NULL,
    manual_decreases NUMERIC(19,4) NOT NULL,
    damaged_quantity NUMERIC(19,4) NOT NULL,
    expired_quantity NUMERIC(19,4) NOT NULL,
    transfer_in NUMERIC(19,4) NOT NULL,
    transfer_out NUMERIC(19,4) NOT NULL,
    stock_count_variances NUMERIC(19,4) NOT NULL,
    low_stock_products BIGINT NOT NULL,
    negative_stock_products BIGINT NOT NULL,
    inventory_value_movement NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_inventory_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id)
);

CREATE TABLE end_of_day_cashier_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    cashier_id UUID NOT NULL,
    cashier_name VARCHAR(180) NOT NULL,
    transaction_count BIGINT NOT NULL,
    gross_sales NUMERIC(12,2) NOT NULL,
    net_sales NUMERIC(12,2) NOT NULL,
    refund_total NUMERIC(12,2) NOT NULL,
    void_count BIGINT NOT NULL,
    discount_total NUMERIC(12,2) NOT NULL,
    price_override_count BIGINT NOT NULL,
    cash_handled NUMERIC(12,2) NOT NULL,
    lottery_sales NUMERIC(12,2) NOT NULL,
    lottery_payouts NUMERIC(12,2) NOT NULL,
    average_transaction_value NUMERIC(12,2) NOT NULL,
    first_activity_at TIMESTAMPTZ,
    last_activity_at TIMESTAMPTZ,
    registers_used TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_cashier_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id),
    CONSTRAINT fk_eod_cashier_summaries_cashier FOREIGN KEY (cashier_id) REFERENCES security_users(id),
    CONSTRAINT uq_eod_cashier_summaries_cashier UNIQUE (report_id, cashier_id)
);

CREATE TABLE end_of_day_exception_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    exception_type VARCHAR(80) NOT NULL,
    count BIGINT NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_exception_summaries_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id),
    CONSTRAINT uq_eod_exception_summaries_type UNIQUE (report_id, exception_type)
);

CREATE TABLE end_of_day_sign_offs (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE,
    manager_user_id UUID NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    notes VARCHAR(1000),
    variance_explanation VARCHAR(1000),
    confirmation_accepted BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_eod_sign_offs_report FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id),
    CONSTRAINT fk_eod_sign_offs_manager FOREIGN KEY (manager_user_id) REFERENCES security_users(id),
    CONSTRAINT ck_eod_sign_off_confirmation CHECK (confirmation_accepted = TRUE)
);

CREATE TABLE business_day_configurations (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL UNIQUE,
    require_all_registers_closed BOOLEAN NOT NULL,
    allow_force_close BOOLEAN NOT NULL,
    cash_variance_explanation_threshold NUMERIC(12,2) NOT NULL,
    require_manager_sign_off BOOLEAN NOT NULL,
    block_next_business_day_until_previous_close BOOLEAN NOT NULL,
    automatically_open_business_day BOOLEAN NOT NULL,
    automatically_generate_report_after_final_register_closes BOOLEAN NOT NULL,
    enable_compact_thermal_eod_summary BOOLEAN NOT NULL,
    report_retention_days INTEGER NOT NULL,
    closing_reminder_time TIME,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_business_day_configurations_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT ck_business_day_configurations_retention CHECK (report_retention_days > 0),
    CONSTRAINT ck_business_day_configurations_variance CHECK (cash_variance_explanation_threshold >= 0)
);

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000260', 'BUSINESS_DAY_VIEW', 'View business days.'),
    ('00000000-0000-0000-0000-000000000261', 'BUSINESS_DAY_OPEN', 'Open business days.'),
    ('00000000-0000-0000-0000-000000000262', 'BUSINESS_DAY_CLOSE', 'Start and complete business-day closing.'),
    ('00000000-0000-0000-0000-000000000263', 'BUSINESS_DAY_FORCE_CLOSE', 'Force-close business days with unresolved blockers.'),
    ('00000000-0000-0000-0000-000000000264', 'BUSINESS_DAY_REOPEN', 'Reopen closed business days.'),
    ('00000000-0000-0000-0000-000000000265', 'END_OF_DAY_REPORT_VIEW', 'View end-of-day reports.'),
    ('00000000-0000-0000-0000-000000000266', 'END_OF_DAY_REPORT_EXPORT', 'Export end-of-day reports.'),
    ('00000000-0000-0000-0000-000000000267', 'END_OF_DAY_REPORT_PRINT', 'Print end-of-day reports.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000360'::UUID, 'OWNER', 'BUSINESS_DAY_VIEW'),
        ('00000000-0000-0000-0000-000000000361'::UUID, 'OWNER', 'BUSINESS_DAY_OPEN'),
        ('00000000-0000-0000-0000-000000000362'::UUID, 'OWNER', 'BUSINESS_DAY_CLOSE'),
        ('00000000-0000-0000-0000-000000000363'::UUID, 'OWNER', 'BUSINESS_DAY_FORCE_CLOSE'),
        ('00000000-0000-0000-0000-000000000364'::UUID, 'OWNER', 'BUSINESS_DAY_REOPEN'),
        ('00000000-0000-0000-0000-000000000365'::UUID, 'OWNER', 'END_OF_DAY_REPORT_VIEW'),
        ('00000000-0000-0000-0000-000000000366'::UUID, 'OWNER', 'END_OF_DAY_REPORT_EXPORT'),
        ('00000000-0000-0000-0000-000000000367'::UUID, 'OWNER', 'END_OF_DAY_REPORT_PRINT'),
        ('00000000-0000-0000-0000-000000000460'::UUID, 'MANAGER', 'BUSINESS_DAY_VIEW'),
        ('00000000-0000-0000-0000-000000000461'::UUID, 'MANAGER', 'BUSINESS_DAY_OPEN'),
        ('00000000-0000-0000-0000-000000000462'::UUID, 'MANAGER', 'BUSINESS_DAY_CLOSE'),
        ('00000000-0000-0000-0000-000000000465'::UUID, 'MANAGER', 'END_OF_DAY_REPORT_VIEW'),
        ('00000000-0000-0000-0000-000000000466'::UUID, 'MANAGER', 'END_OF_DAY_REPORT_EXPORT'),
        ('00000000-0000-0000-0000-000000000467'::UUID, 'MANAGER', 'END_OF_DAY_REPORT_PRINT')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
