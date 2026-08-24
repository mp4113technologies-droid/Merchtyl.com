ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_by_platform_user_id UUID,
    ADD COLUMN IF NOT EXISTS closure_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS reactivated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reactivated_by_platform_user_id UUID;

ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_closed_by_platform_user FOREIGN KEY (closed_by_platform_user_id) REFERENCES platform_users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_tenants_reactivated_by_platform_user FOREIGN KEY (reactivated_by_platform_user_id) REFERENCES platform_users(id) ON DELETE SET NULL;

ALTER TABLE tenant_status_history
    ADD COLUMN IF NOT EXISTS tenant_code_snapshot VARCHAR(64),
    ADD COLUMN IF NOT EXISTS notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(120);

UPDATE tenant_status_history history
SET tenant_code_snapshot = tenant.tenant_code
FROM tenants tenant
WHERE history.tenant_id = tenant.id
  AND history.tenant_code_snapshot IS NULL;

ALTER TABLE tenant_status_history DROP CONSTRAINT IF EXISTS fk_tenant_status_history_tenant;
ALTER TABLE tenant_status_history ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE tenant_status_history
    ADD CONSTRAINT fk_tenant_status_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE SET NULL;

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:TENANT_REOPEN')::UUID, 'TENANT_REOPEN', 'Reopen a closed merchant tenant through an elevated platform workflow.'),
    (md5('permission:TENANT_DELETE_EMPTY')::UUID, 'TENANT_DELETE_EMPTY', 'Hard-delete an eligible empty merchant tenant created by mistake.'),
    (md5('permission:TENANT_DELETION_ELIGIBILITY_VIEW')::UUID, 'TENANT_DELETION_ELIGIBILITY_VIEW', 'View merchant empty-deletion eligibility and blockers.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'TENANT_REOPEN',
    'TENANT_DELETE_EMPTY',
    'TENANT_DELETION_ELIGIBILITY_VIEW'
)
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_tenants_closed_at ON tenants(closed_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenants_reactivated_at ON tenants(reactivated_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_status_history_changed_at ON tenant_status_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_status_history_tenant_code_snapshot ON tenant_status_history(tenant_code_snapshot, created_at DESC);

DROP VIEW platform_tenant_summary;

CREATE VIEW platform_tenant_summary AS
SELECT
    tenant.id,
    tenant.tenant_code,
    tenant.legal_name,
    tenant.display_name,
    tenant.status,
    tenant.country_code,
    tenant.administrative_division_code,
    tenant.default_currency_code,
    tenant.primary_timezone,
    tenant.default_tax_region_code,
    owner.email AS primary_owner_email,
    subscription.plan_code AS subscription_plan,
    onboarding.current_stage AS onboarding_stage,
    COALESCE(store_counts.store_count, 0) AS store_count,
    COALESCE(user_counts.user_count, 0) AS user_count,
    tenant.created_at,
    tenant.activated_at,
    tenant.suspended_at,
    tenant.suspended_by_platform_user_id,
    tenant.suspension_reason,
    tenant.closed_at,
    tenant.closed_by_platform_user_id,
    tenant.closure_reason,
    tenant.reactivated_at,
    tenant.reactivated_by_platform_user_id,
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
