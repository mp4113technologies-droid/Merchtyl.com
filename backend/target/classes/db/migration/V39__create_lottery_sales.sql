ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT',
    'SALE_CASH_RECEIPT',
    'SALE_CHANGE_GIVEN',
    'LOTTERY_SALE_CASH',
    'CASH_REFUND',
    'CASH_MOVEMENT',
    'SESSION_CLOSE_ADJUSTMENT'
));

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    (source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH') AND direction = 'IN')
    OR (source_type IN ('SALE_CHANGE_GIVEN', 'CASH_REFUND') AND direction = 'OUT')
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);

CREATE TABLE lottery_sales (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL,
    operator_reference VARCHAR(180),
    ticket_reference VARCHAR(180),
    game_type VARCHAR(32) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    device_id UUID NOT NULL,
    cashier_id UUID NOT NULL,
    register_session_id UUID,
    status VARCHAR(32) NOT NULL,
    operation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_sales_operator FOREIGN KEY (operator_id) REFERENCES lottery_operators (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_sales_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_sales_register FOREIGN KEY (register_id) REFERENCES registers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_sales_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_sales_cashier FOREIGN KEY (cashier_id) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_sales_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lottery_sales_operation_id UNIQUE (operation_id),
    CONSTRAINT ck_lottery_sales_operator_reference_nonblank CHECK (operator_reference IS NULL OR btrim(operator_reference) <> ''),
    CONSTRAINT ck_lottery_sales_ticket_reference_nonblank CHECK (ticket_reference IS NULL OR btrim(ticket_reference) <> ''),
    CONSTRAINT ck_lottery_sales_game_type CHECK (game_type IN ('DRAW_TICKET', 'INSTANT_TICKET', 'SPORTS_WAGER', 'BREAKOPEN', 'ONLINE_CREDIT', 'OTHER')),
    CONSTRAINT ck_lottery_sales_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_lottery_sales_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_lottery_sales_payment_method CHECK (payment_method IN ('CASH', 'DEBIT', 'CREDIT', 'GIFT_CARD', 'STORE_CREDIT', 'OTHER')),
    CONSTRAINT ck_lottery_sales_status CHECK (status IN ('RECORDED')),
    CONSTRAINT ck_lottery_sales_cash_session CHECK (payment_method <> 'CASH' OR register_session_id IS NOT NULL)
);

CREATE INDEX idx_lottery_sales_operator_id ON lottery_sales (operator_id);
CREATE INDEX idx_lottery_sales_store_id ON lottery_sales (store_id);
CREATE INDEX idx_lottery_sales_register_id ON lottery_sales (register_id);
CREATE INDEX idx_lottery_sales_device_id ON lottery_sales (device_id);
CREATE INDEX idx_lottery_sales_cashier_id ON lottery_sales (cashier_id);
CREATE INDEX idx_lottery_sales_register_session_id ON lottery_sales (register_session_id);
CREATE INDEX idx_lottery_sales_status ON lottery_sales (status);
CREATE INDEX idx_lottery_sales_occurred_at ON lottery_sales (occurred_at DESC);

INSERT INTO security_permissions (id, code, description)
VALUES ('00000000-0000-0000-0000-000000000242', 'LOTTERY_SALE_RECORD', 'Record lottery sales.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000342'::UUID, 'OWNER', 'LOTTERY_SALE_RECORD'),
        ('00000000-0000-0000-0000-000000000442'::UUID, 'MANAGER', 'LOTTERY_SALE_RECORD'),
        ('00000000-0000-0000-0000-000000000542'::UUID, 'CASHIER', 'LOTTERY_SALE_RECORD')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
