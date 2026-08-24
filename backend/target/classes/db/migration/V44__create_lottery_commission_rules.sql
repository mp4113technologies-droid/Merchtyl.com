CREATE TABLE lottery_commission_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    operator_id UUID NOT NULL,
    jurisdiction_id UUID NOT NULL,
    store_id UUID NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    commission_rate_percent NUMERIC(9, 4),
    fixed_amount NUMERIC(19, 2),
    currency_code VARCHAR(3),
    fixed_period VARCHAR(16),
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_commission_rules_operator FOREIGN KEY (operator_id) REFERENCES lottery_operators (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_commission_rules_jurisdiction FOREIGN KEY (jurisdiction_id) REFERENCES tax_jurisdictions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_commission_rules_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lottery_commission_rules_type CHECK (rule_type IN (
        'PERCENT_OF_SALES',
        'PERCENT_OF_PAYOUT',
        'FIXED_PER_TRANSACTION',
        'FIXED_PER_PERIOD',
        'MANUAL'
    )),
    CONSTRAINT ck_lottery_commission_rules_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_lottery_commission_rules_period CHECK (fixed_period IS NULL OR fixed_period IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    CONSTRAINT ck_lottery_commission_rules_effective_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_lottery_commission_rules_rate CHECK (
        commission_rate_percent IS NULL OR (commission_rate_percent > 0 AND commission_rate_percent <= 100)
    ),
    CONSTRAINT ck_lottery_commission_rules_fixed_amount CHECK (
        fixed_amount IS NULL OR fixed_amount > 0
    ),
    CONSTRAINT ck_lottery_commission_rules_type_amounts CHECK (
        (rule_type IN ('PERCENT_OF_SALES', 'PERCENT_OF_PAYOUT')
            AND commission_rate_percent IS NOT NULL
            AND fixed_amount IS NULL
            AND currency_code IS NULL
            AND fixed_period IS NULL)
        OR (rule_type = 'FIXED_PER_TRANSACTION'
            AND commission_rate_percent IS NULL
            AND fixed_amount IS NOT NULL
            AND currency_code IS NOT NULL
            AND fixed_period IS NULL)
        OR (rule_type = 'FIXED_PER_PERIOD'
            AND commission_rate_percent IS NULL
            AND fixed_amount IS NOT NULL
            AND currency_code IS NOT NULL
            AND fixed_period IS NOT NULL)
        OR (rule_type = 'MANUAL'
            AND commission_rate_percent IS NULL
            AND fixed_amount IS NULL
            AND currency_code IS NULL
            AND fixed_period IS NULL)
    )
);

CREATE INDEX idx_lottery_commission_rules_operator_id ON lottery_commission_rules (operator_id);
CREATE INDEX idx_lottery_commission_rules_jurisdiction_id ON lottery_commission_rules (jurisdiction_id);
CREATE INDEX idx_lottery_commission_rules_store_id ON lottery_commission_rules (store_id);
CREATE INDEX idx_lottery_commission_rules_type ON lottery_commission_rules (rule_type);
CREATE INDEX idx_lottery_commission_rules_status ON lottery_commission_rules (status);
CREATE INDEX idx_lottery_commission_rules_effective_period ON lottery_commission_rules (effective_from, effective_to);

INSERT INTO security_permissions (id, code, description)
VALUES ('00000000-0000-0000-0000-000000000246', 'LOTTERY_COMMISSION_RULE_MANAGE', 'Manage lottery commission rules.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000346'::UUID, 'OWNER', 'LOTTERY_COMMISSION_RULE_MANAGE'),
        ('00000000-0000-0000-0000-000000000446'::UUID, 'MANAGER', 'LOTTERY_COMMISSION_RULE_MANAGE')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
