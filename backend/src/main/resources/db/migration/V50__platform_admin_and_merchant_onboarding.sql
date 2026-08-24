ALTER TABLE security_roles DROP CONSTRAINT IF EXISTS ck_security_roles_name;
ALTER TABLE security_roles
    ADD CONSTRAINT ck_security_roles_name CHECK (name IN (
        'OWNER',
        'MANAGER',
        'TENANT_OWNER',
        'STORE_MANAGER',
        'CASHIER',
        'PLATFORM_SUPER_ADMIN',
        'PLATFORM_SUPPORT_ADMIN'
    ));

ALTER TABLE security_users
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

CREATE TABLE platform_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_users_email UNIQUE (email),
    CONSTRAINT ck_platform_users_role CHECK (role IN ('PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT_ADMIN')),
    CONSTRAINT ck_platform_users_email_nonblank CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_platform_users_display_name_nonblank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ck_platform_users_password_hash_nonblank CHECK (length(trim(password_hash)) > 0)
);

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    status VARCHAR(64) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    default_currency_code VARCHAR(3) NOT NULL,
    primary_timezone VARCHAR(64) NOT NULL,
    created_by_platform_user_id UUID,
    activated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    suspended_by_platform_user_id UUID,
    suspension_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenants_tenant_code UNIQUE (tenant_code),
    CONSTRAINT ck_tenants_status CHECK (status IN (
        'PENDING_ONBOARDING',
        'PENDING_OWNER_ACTIVATION',
        'ACTIVE',
        'SUSPENDED',
        'CLOSED',
        'REJECTED'
    )),
    CONSTRAINT ck_tenants_code_nonblank CHECK (length(trim(tenant_code)) > 0),
    CONSTRAINT ck_tenants_legal_name_nonblank CHECK (length(trim(legal_name)) > 0),
    CONSTRAINT ck_tenants_display_name_nonblank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ck_tenants_country_code CHECK (country_code = upper(country_code) AND length(country_code) = 2),
    CONSTRAINT ck_tenants_currency_code CHECK (default_currency_code = upper(default_currency_code) AND length(default_currency_code) = 3),
    CONSTRAINT ck_tenants_timezone_nonblank CHECK (length(trim(primary_timezone)) > 0),
    CONSTRAINT fk_tenants_created_by_platform_user FOREIGN KEY (created_by_platform_user_id) REFERENCES platform_users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tenants_suspended_by_platform_user FOREIGN KEY (suspended_by_platform_user_id) REFERENCES platform_users (id) ON DELETE SET NULL
);

CREATE TABLE merchant_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_business_name VARCHAR(255) NOT NULL,
    operating_name VARCHAR(180) NOT NULL,
    business_number VARCHAR(80),
    contact_name VARCHAR(180) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(40),
    billing_address VARCHAR(1000),
    country_code VARCHAR(2) NOT NULL,
    administrative_division_code VARCHAR(32),
    postal_code VARCHAR(32),
    industry_type VARCHAR(120),
    estimated_store_count INTEGER,
    notes VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_merchant_profiles_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_merchant_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE TABLE tenant_onboardings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    current_stage VARCHAR(64) NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_onboardings_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_onboardings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_onboardings_stage CHECK (current_stage IN (
        'MERCHANT_DETAILS',
        'OWNER_ACCOUNT',
        'OWNER_INVITATION',
        'OWNER_ACTIVATION',
        'ORGANIZATION_SETUP',
        'FIRST_STORE_SETUP',
        'COMPLETED'
    ))
);

CREATE TABLE tenant_onboarding_stages (
    id UUID PRIMARY KEY,
    tenant_onboarding_id UUID NOT NULL,
    stage VARCHAR(64) NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_onboarding_stages_stage UNIQUE (tenant_onboarding_id, stage),
    CONSTRAINT fk_tenant_onboarding_stages_onboarding FOREIGN KEY (tenant_onboarding_id) REFERENCES tenant_onboardings (id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_onboarding_stages_stage CHECK (stage IN (
        'MERCHANT_DETAILS',
        'OWNER_ACCOUNT',
        'OWNER_INVITATION',
        'OWNER_ACTIVATION',
        'ORGANIZATION_SETUP',
        'FIRST_STORE_SETUP',
        'COMPLETED'
    ))
);

CREATE TABLE tenant_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_code VARCHAR(80) NOT NULL,
    status VARCHAR(64) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    trial_ends_at TIMESTAMPTZ,
    renews_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    maximum_stores INTEGER,
    maximum_users INTEGER,
    features JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_subscriptions_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'))
);

CREATE TABLE tenant_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    old_status VARCHAR(64),
    new_status VARCHAR(64) NOT NULL,
    changed_by_platform_user_id UUID,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenant_status_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_status_history_actor FOREIGN KEY (changed_by_platform_user_id) REFERENCES platform_users (id) ON DELETE SET NULL
);

CREATE TABLE tenant_owner_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by_platform_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tenant_owner_invitations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_owner_invitations_owner FOREIGN KEY (owner_user_id) REFERENCES security_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_owner_invitations_actor FOREIGN KEY (created_by_platform_user_id) REFERENCES platform_users (id) ON DELETE SET NULL,
    CONSTRAINT uq_tenant_owner_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_tenant_owner_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'))
);

CREATE UNIQUE INDEX uq_tenant_owner_invitations_active_email
    ON tenant_owner_invitations (tenant_id, lower(email))
    WHERE status = 'PENDING';

CREATE TABLE support_access_sessions (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    approved_by UUID,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    status VARCHAR(64) NOT NULL,
    read_only BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_support_access_sessions_platform_user FOREIGN KEY (platform_user_id) REFERENCES platform_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_support_access_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_support_access_sessions_approved_by FOREIGN KEY (approved_by) REFERENCES platform_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_support_access_sessions_status CHECK (status IN ('REQUESTED', 'APPROVED', 'ACTIVE', 'EXPIRED', 'ENDED', 'DENIED')),
    CONSTRAINT ck_support_access_sessions_reason_nonblank CHECK (length(trim(reason)) > 0)
);

ALTER TABLE security_users
    ADD CONSTRAINT fk_security_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT;

ALTER TABLE stores
    ADD CONSTRAINT fk_stores_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT;

CREATE INDEX idx_platform_users_role ON platform_users (role);
CREATE INDEX idx_platform_users_enabled ON platform_users (enabled);
CREATE INDEX idx_tenants_status ON tenants (status);
CREATE INDEX idx_tenants_created_at ON tenants (created_at DESC);
CREATE INDEX idx_security_users_tenant ON security_users (tenant_id);
CREATE INDEX idx_stores_tenant ON stores (tenant_id);
CREATE INDEX idx_tenant_status_history_tenant ON tenant_status_history (tenant_id, created_at DESC);
CREATE INDEX idx_support_access_sessions_tenant ON support_access_sessions (tenant_id, expires_at);

CREATE VIEW platform_tenant_summary AS
SELECT
    tenant.id,
    tenant.tenant_code,
    tenant.legal_name,
    tenant.display_name,
    tenant.status,
    tenant.country_code,
    tenant.default_currency_code,
    tenant.primary_timezone,
    owner.email AS primary_owner_email,
    subscription.plan_code AS subscription_plan,
    onboarding.current_stage AS onboarding_stage,
    COALESCE(store_counts.store_count, 0) AS store_count,
    COALESCE(user_counts.user_count, 0) AS user_count,
    tenant.created_at,
    tenant.activated_at,
    tenant.suspended_at,
    tenant.version
FROM tenants tenant
LEFT JOIN LATERAL (
    SELECT security_users.email
    FROM security_users
    JOIN security_user_roles user_role ON user_role.user_id = security_users.id
    JOIN security_roles role ON role.id = user_role.role_id
    WHERE security_users.tenant_id = tenant.id
      AND role.name IN ('TENANT_OWNER', 'OWNER')
    ORDER BY security_users.created_at ASC
    LIMIT 1
) owner ON TRUE
LEFT JOIN tenant_subscriptions subscription ON subscription.tenant_id = tenant.id
LEFT JOIN tenant_onboardings onboarding ON onboarding.tenant_id = tenant.id
LEFT JOIN LATERAL (
    SELECT count(*) AS store_count
    FROM stores
    WHERE stores.tenant_id = tenant.id
) store_counts ON TRUE
LEFT JOIN LATERAL (
    SELECT count(*) AS user_count
    FROM security_users
    WHERE security_users.tenant_id = tenant.id
) user_counts ON TRUE;

INSERT INTO security_roles (id, name, description, system_role)
VALUES
    ('00000000-0000-0000-0000-000000000104', 'TENANT_OWNER', 'Merchant tenant owner with full tenant administration access.', TRUE),
    ('00000000-0000-0000-0000-000000000105', 'STORE_MANAGER', 'Store manager with assigned-store management access.', TRUE),
    ('00000000-0000-0000-0000-000000000106', 'PLATFORM_SUPER_ADMIN', 'Platform operator with full platform administration access.', TRUE),
    ('00000000-0000-0000-0000-000000000107', 'PLATFORM_SUPPORT_ADMIN', 'Platform operator with limited support administration access.', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-0000000005a0', 'PLATFORM_DASHBOARD_VIEW', 'View platform dashboard.'),
    ('00000000-0000-0000-0000-0000000005a1', 'TENANT_CREATE', 'Create merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a2', 'TENANT_VIEW', 'View merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a3', 'TENANT_UPDATE', 'Update merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a4', 'TENANT_ACTIVATE', 'Activate merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a5', 'TENANT_SUSPEND', 'Suspend merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a6', 'TENANT_REACTIVATE', 'Reactivate merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a7', 'TENANT_CLOSE', 'Close merchant tenants.'),
    ('00000000-0000-0000-0000-0000000005a8', 'TENANT_OWNER_INVITE', 'Invite merchant tenant owners.'),
    ('00000000-0000-0000-0000-0000000005a9', 'TENANT_OWNER_RESEND_INVITE', 'Resend merchant owner invitations.'),
    ('00000000-0000-0000-0000-0000000005aa', 'TENANT_OWNER_DISABLE', 'Disable merchant tenant owners.'),
    ('00000000-0000-0000-0000-0000000005ab', 'SUBSCRIPTION_VIEW', 'View merchant subscriptions.'),
    ('00000000-0000-0000-0000-0000000005ac', 'SUBSCRIPTION_UPDATE', 'Update merchant subscriptions.'),
    ('00000000-0000-0000-0000-0000000005ad', 'PLATFORM_USER_VIEW', 'View platform users.'),
    ('00000000-0000-0000-0000-0000000005ae', 'PLATFORM_USER_CREATE', 'Create platform users.'),
    ('00000000-0000-0000-0000-0000000005af', 'PLATFORM_USER_UPDATE', 'Update platform users.'),
    ('00000000-0000-0000-0000-0000000005b0', 'PLATFORM_USER_DISABLE', 'Disable platform users.'),
    ('00000000-0000-0000-0000-0000000005b1', 'SUPPORT_ACCESS_REQUEST', 'Request controlled merchant support access.'),
    ('00000000-0000-0000-0000-0000000005b2', 'SUPPORT_ACCESS_APPROVE', 'Approve controlled merchant support access.'),
    ('00000000-0000-0000-0000-0000000005b3', 'PLATFORM_AUDIT_VIEW', 'View platform audit events.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'PLATFORM_DASHBOARD_VIEW',
    'TENANT_CREATE',
    'TENANT_VIEW',
    'TENANT_UPDATE',
    'TENANT_ACTIVATE',
    'TENANT_SUSPEND',
    'TENANT_REACTIVATE',
    'TENANT_CLOSE',
    'TENANT_OWNER_INVITE',
    'TENANT_OWNER_RESEND_INVITE',
    'TENANT_OWNER_DISABLE',
    'SUBSCRIPTION_VIEW',
    'SUBSCRIPTION_UPDATE',
    'PLATFORM_USER_VIEW',
    'PLATFORM_USER_CREATE',
    'PLATFORM_USER_UPDATE',
    'PLATFORM_USER_DISABLE',
    'SUPPORT_ACCESS_REQUEST',
    'SUPPORT_ACCESS_APPROVE',
    'PLATFORM_AUDIT_VIEW'
)
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'PLATFORM_DASHBOARD_VIEW',
    'TENANT_VIEW',
    'TENANT_OWNER_RESEND_INVITE',
    'SUBSCRIPTION_VIEW',
    'SUPPORT_ACCESS_REQUEST',
    'PLATFORM_AUDIT_VIEW'
)
WHERE role.name = 'PLATFORM_SUPPORT_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || tenant_role.name || ':' || permission.code)::UUID, tenant_role.id, permission.id
FROM security_roles tenant_role
JOIN security_roles legacy_role ON legacy_role.name = 'OWNER'
JOIN security_role_permissions legacy_grant ON legacy_grant.role_id = legacy_role.id
JOIN security_permissions permission ON permission.id = legacy_grant.permission_id
WHERE tenant_role.name = 'TENANT_OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || tenant_role.name || ':' || permission.code)::UUID, tenant_role.id, permission.id
FROM security_roles tenant_role
JOIN security_roles legacy_role ON legacy_role.name = 'MANAGER'
JOIN security_role_permissions legacy_grant ON legacy_grant.role_id = legacy_role.id
JOIN security_permissions permission ON permission.id = legacy_grant.permission_id
WHERE tenant_role.name = 'STORE_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
