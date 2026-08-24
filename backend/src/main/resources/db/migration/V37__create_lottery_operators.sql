CREATE TABLE lottery_operators (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    jurisdiction_id UUID NOT NULL,
    support_contact VARCHAR(1000),
    settlement_frequency VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_operators_jurisdiction FOREIGN KEY (jurisdiction_id) REFERENCES tax_jurisdictions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lottery_operators_code UNIQUE (code),
    CONSTRAINT ck_lottery_operators_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_lottery_operators_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_lottery_operators_support_contact_nonblank CHECK (support_contact IS NULL OR length(trim(support_contact)) > 0),
    CONSTRAINT ck_lottery_operators_settlement_frequency CHECK (settlement_frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_lottery_operators_jurisdiction_id ON lottery_operators (jurisdiction_id);
CREATE INDEX idx_lottery_operators_name ON lottery_operators (name);
CREATE INDEX idx_lottery_operators_active ON lottery_operators (active);

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000240', 'LOTTERY_VIEW', 'View lottery configuration.'),
    ('00000000-0000-0000-0000-000000000241', 'LOTTERY_MANAGE', 'Manage lottery configuration.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000340'::UUID, 'OWNER', 'LOTTERY_VIEW'),
        ('00000000-0000-0000-0000-000000000341'::UUID, 'OWNER', 'LOTTERY_MANAGE'),
        ('00000000-0000-0000-0000-000000000440'::UUID, 'MANAGER', 'LOTTERY_VIEW'),
        ('00000000-0000-0000-0000-000000000441'::UUID, 'MANAGER', 'LOTTERY_MANAGE')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
