ALTER TABLE security_users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_failed_login_at TIMESTAMPTZ,
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN lock_reason VARCHAR(64),
    ADD COLUMN password_reset_at TIMESTAMPTZ;

ALTER TABLE security_users
    ADD CONSTRAINT ck_security_users_failed_login_attempts CHECK (failed_login_attempts >= 0),
    ADD CONSTRAINT ck_security_users_lock_reason CHECK (lock_reason IS NULL OR lock_reason IN (
        'FAILED_LOGIN_ATTEMPTS', 'ADMINISTRATIVE_LOCK', 'SECURITY_REVIEW'
    ));

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(40) NOT NULL DEFAULT 'PASSWORD_RESET',
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_type VARCHAR(40) NOT NULL,
    created_by_user_id UUID,
    request_ip VARCHAR(64),
    correlation_id VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES security_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_password_reset_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_password_reset_tokens_purpose CHECK (purpose = 'PASSWORD_RESET'),
    CONSTRAINT ck_password_reset_tokens_creator CHECK (created_by_type IN ('SELF_SERVICE', 'PLATFORM_ADMIN', 'TENANT_ADMIN'))
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id, created_at DESC);
CREATE INDEX idx_password_reset_tokens_tenant ON password_reset_tokens(tenant_id, created_at DESC);
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens(expires_at);
CREATE INDEX idx_password_reset_tokens_unused ON password_reset_tokens(user_id, used_at, revoked_at);

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:TENANT_USER_SEND_PASSWORD_RESET')::UUID, 'TENANT_USER_SEND_PASSWORD_RESET', 'Send password reset links to merchant users.'),
    (md5('permission:TENANT_USER_UNLOCK')::UUID, 'TENANT_USER_UNLOCK', 'Unlock merchant user accounts.'),
    (md5('permission:USER_SEND_PASSWORD_RESET')::UUID, 'USER_SEND_PASSWORD_RESET', 'Send password reset links within tenant user-management scope.'),
    (md5('permission:PASSWORD_RESET_DELIVERY_VIEW')::UUID, 'PASSWORD_RESET_DELIVERY_VIEW', 'View password reset email delivery status.'),
    (md5('permission:PASSWORD_RESET_DELIVERY_RETRY')::UUID, 'PASSWORD_RESET_DELIVERY_RETRY', 'Retry password reset email delivery.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'TENANT_USER_SEND_PASSWORD_RESET', 'TENANT_USER_UNLOCK',
    'PASSWORD_RESET_DELIVERY_VIEW', 'PASSWORD_RESET_DELIVERY_RETRY'
)
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'USER_SEND_PASSWORD_RESET'
WHERE role.name = 'TENANT_OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
