ALTER TABLE lottery_sales DROP CONSTRAINT ck_lottery_sales_status;
ALTER TABLE lottery_sales ADD CONSTRAINT ck_lottery_sales_status CHECK (status IN ('RECORDED', 'CANCELLED'));

CREATE TABLE lottery_sale_cancellations (
    id UUID PRIMARY KEY,
    original_sale_id UUID NOT NULL,
    cancelled_by UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    cash_returned BOOLEAN NOT NULL,
    operation_id UUID NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_sale_cancellations_original_sale FOREIGN KEY (original_sale_id) REFERENCES lottery_sales (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_sale_cancellations_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lottery_sale_cancellations_original_sale UNIQUE (original_sale_id),
    CONSTRAINT uq_lottery_sale_cancellations_operation_id UNIQUE (operation_id),
    CONSTRAINT ck_lottery_sale_cancellations_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_lottery_sale_cancellations_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_lottery_sale_cancellations_reason_nonblank CHECK (btrim(reason) <> '')
);

CREATE INDEX idx_lottery_sale_cancellations_cancelled_by ON lottery_sale_cancellations (cancelled_by);
CREATE INDEX idx_lottery_sale_cancellations_cancelled_at ON lottery_sale_cancellations (cancelled_at DESC);

CREATE TABLE lottery_payout_reversals (
    id UUID PRIMARY KEY,
    original_payout_id UUID NOT NULL,
    reversed_by UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    operation_id UUID NOT NULL,
    reversed_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_payout_reversals_original_payout FOREIGN KEY (original_payout_id) REFERENCES lottery_payouts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payout_reversals_reversed_by FOREIGN KEY (reversed_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lottery_payout_reversals_original_payout UNIQUE (original_payout_id),
    CONSTRAINT uq_lottery_payout_reversals_operation_id UNIQUE (operation_id),
    CONSTRAINT ck_lottery_payout_reversals_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_lottery_payout_reversals_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_lottery_payout_reversals_reason_nonblank CHECK (btrim(reason) <> '')
);

CREATE INDEX idx_lottery_payout_reversals_reversed_by ON lottery_payout_reversals (reversed_by);
CREATE INDEX idx_lottery_payout_reversals_reversed_at ON lottery_payout_reversals (reversed_at DESC);

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT',
    'SALE_CASH_RECEIPT',
    'SALE_CHANGE_GIVEN',
    'LOTTERY_SALE_CASH',
    'LOTTERY_PAYOUT_CASH',
    'LOTTERY_PAYOUT_REVERSAL',
    'CASH_REFUND',
    'CASH_MOVEMENT',
    'SESSION_CLOSE_ADJUSTMENT'
));

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    (source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH', 'LOTTERY_PAYOUT_REVERSAL') AND direction = 'IN')
    OR (source_type IN ('SALE_CHANGE_GIVEN', 'LOTTERY_PAYOUT_CASH', 'CASH_REFUND') AND direction = 'OUT')
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);

INSERT INTO security_permissions (id, code, description)
VALUES ('00000000-0000-0000-0000-000000000245', 'LOTTERY_SALE_CANCEL', 'Cancel lottery sales and return cash where applicable.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000345'::UUID, 'OWNER', 'LOTTERY_SALE_CANCEL'),
        ('00000000-0000-0000-0000-000000000445'::UUID, 'MANAGER', 'LOTTERY_SALE_CANCEL'),
        ('00000000-0000-0000-0000-000000000545'::UUID, 'CASHIER', 'LOTTERY_SALE_CANCEL')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
