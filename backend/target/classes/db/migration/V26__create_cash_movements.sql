CREATE TABLE cash_movements (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    register_session_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    notes VARCHAR(1000),
    created_by UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    approval_notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cash_movements_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_movements_register FOREIGN KEY (register_id) REFERENCES registers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_movements_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_movements_created_by FOREIGN KEY (created_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_movements_approved_by FOREIGN KEY (approved_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_movements_type CHECK (type IN (
        'CASH_IN',
        'CASH_OUT',
        'SAFE_DROP',
        'FLOAT_ADD',
        'FLOAT_REMOVE',
        'EXPENSE',
        'BANK_DEPOSIT',
        'CORRECTION'
    )),
    CONSTRAINT ck_cash_movements_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_cash_movements_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_cash_movements_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_cash_movements_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_cash_movements_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> ''),
    CONSTRAINT ck_cash_movements_approval_notes_nonblank CHECK (approval_notes IS NULL OR btrim(approval_notes) <> ''),
    CONSTRAINT ck_cash_movements_approval_pair CHECK ((approved_by IS NULL AND approved_at IS NULL) OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_cash_movements_type_direction CHECK (
        (type IN ('CASH_IN', 'FLOAT_ADD') AND direction = 'IN')
        OR (type IN ('CASH_OUT', 'SAFE_DROP', 'FLOAT_REMOVE', 'EXPENSE', 'BANK_DEPOSIT') AND direction = 'OUT')
        OR type = 'CORRECTION'
    )
);

CREATE INDEX idx_cash_movements_store ON cash_movements (store_id);
CREATE INDEX idx_cash_movements_register ON cash_movements (register_id);
CREATE INDEX idx_cash_movements_register_session ON cash_movements (register_session_id);
CREATE INDEX idx_cash_movements_type ON cash_movements (type);
CREATE INDEX idx_cash_movements_occurred_at ON cash_movements (occurred_at DESC);

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000223', 'CASH_MOVEMENT_CREATE', 'Create cash movements.'),
    ('00000000-0000-0000-0000-000000000224', 'CASH_MOVEMENT_VIEW', 'View cash movements.'),
    ('00000000-0000-0000-0000-000000000225', 'CASH_MOVEMENT_APPROVE', 'Approve configured cash movements.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000323'::UUID, 'OWNER', 'CASH_MOVEMENT_CREATE'),
        ('00000000-0000-0000-0000-000000000324'::UUID, 'OWNER', 'CASH_MOVEMENT_VIEW'),
        ('00000000-0000-0000-0000-000000000325'::UUID, 'OWNER', 'CASH_MOVEMENT_APPROVE'),
        ('00000000-0000-0000-0000-000000000421'::UUID, 'MANAGER', 'CASH_MOVEMENT_CREATE'),
        ('00000000-0000-0000-0000-000000000422'::UUID, 'MANAGER', 'CASH_MOVEMENT_VIEW'),
        ('00000000-0000-0000-0000-000000000423'::UUID, 'MANAGER', 'CASH_MOVEMENT_APPROVE'),
        ('00000000-0000-0000-0000-000000000521'::UUID, 'CASHIER', 'CASH_MOVEMENT_CREATE'),
        ('00000000-0000-0000-0000-000000000522'::UUID, 'CASHIER', 'CASH_MOVEMENT_VIEW')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
