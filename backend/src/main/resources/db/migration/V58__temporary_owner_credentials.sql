ALTER TABLE security_users
    ADD COLUMN IF NOT EXISTS temporary_password_issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS temporary_password_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS first_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credentials_issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credentials_delivery_status VARCHAR(40);

CREATE TABLE IF NOT EXISTS first_login_password_change_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    purpose VARCHAR(80) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_first_login_password_tokens_user FOREIGN KEY (user_id) REFERENCES security_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_first_login_password_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_first_login_password_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_first_login_password_tokens_hash_nonblank CHECK (length(trim(token_hash)) > 0),
    CONSTRAINT ck_first_login_password_tokens_purpose CHECK (purpose IN ('FIRST_LOGIN_PASSWORD_CHANGE')),
    CONSTRAINT ck_first_login_password_tokens_not_used_and_revoked CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_first_login_password_tokens_user
    ON first_login_password_change_tokens(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_first_login_password_tokens_tenant
    ON first_login_password_change_tokens(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_first_login_password_tokens_expiry
    ON first_login_password_change_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_first_login_password_tokens_active_user
    ON first_login_password_change_tokens(user_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:TENANT_OWNER_ISSUE_TEMPORARY_CREDENTIALS')::UUID, 'TENANT_OWNER_ISSUE_TEMPORARY_CREDENTIALS', 'Issue initial temporary credentials for merchant tenant owners.'),
    (md5('permission:TENANT_OWNER_RESEND_TEMPORARY_CREDENTIALS')::UUID, 'TENANT_OWNER_RESEND_TEMPORARY_CREDENTIALS', 'Reissue temporary credentials for merchant tenant owners.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'TENANT_OWNER_ISSUE_TEMPORARY_CREDENTIALS',
    'TENANT_OWNER_RESEND_TEMPORARY_CREDENTIALS'
)
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
