ALTER TABLE security_user_store_assignments
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS assignment_role VARCHAR(32),
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS assigned_by UUID,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS removed_by UUID,
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS removal_reason VARCHAR(1000);

UPDATE security_user_store_assignments assignment
SET tenant_id = COALESCE(security_user.tenant_id, store.tenant_id),
    assignment_role = CASE
        WHEN EXISTS (
            SELECT 1
            FROM security_user_roles user_role
            JOIN security_roles role ON role.id = user_role.role_id
            WHERE user_role.user_id = security_user.id
              AND role.name IN ('STORE_MANAGER', 'MANAGER')
        ) THEN 'MANAGER'
        ELSE 'CASHIER'
    END
FROM security_users security_user,
     stores store
WHERE assignment.user_id = security_user.id
  AND assignment.store_id = store.id
  AND (assignment.tenant_id IS NULL OR assignment.assignment_role IS NULL);

ALTER TABLE security_user_store_assignments
    ALTER COLUMN assignment_role SET NOT NULL;

ALTER TABLE security_user_store_assignments
    DROP CONSTRAINT IF EXISTS uq_security_user_store_assignments_user_store;

ALTER TABLE security_user_store_assignments
    DROP CONSTRAINT IF EXISTS fk_security_user_store_assignments_tenant,
    DROP CONSTRAINT IF EXISTS fk_security_user_store_assignments_assigned_by,
    DROP CONSTRAINT IF EXISTS fk_security_user_store_assignments_removed_by,
    DROP CONSTRAINT IF EXISTS ck_security_user_store_assignments_role,
    DROP CONSTRAINT IF EXISTS ck_security_user_store_assignments_status;

ALTER TABLE security_user_store_assignments
    ADD CONSTRAINT fk_security_user_store_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_security_user_store_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES security_users (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_security_user_store_assignments_removed_by FOREIGN KEY (removed_by) REFERENCES security_users (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_security_user_store_assignments_role CHECK (assignment_role IN ('MANAGER', 'CASHIER')),
    ADD CONSTRAINT ck_security_user_store_assignments_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'REVOKED', 'PENDING'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_user_store_assignments_tenant_user_store_role
    ON security_user_store_assignments (tenant_id, user_id, store_id, assignment_role);

CREATE INDEX IF NOT EXISTS idx_security_user_store_assignments_tenant_user_active
    ON security_user_store_assignments (tenant_id, user_id, active);

CREATE INDEX IF NOT EXISTS idx_security_user_store_assignments_tenant_store_active
    ON security_user_store_assignments (tenant_id, store_id, active);

CREATE INDEX IF NOT EXISTS idx_security_user_store_assignments_tenant_role_active
    ON security_user_store_assignments (tenant_id, assignment_role, active);

CREATE INDEX IF NOT EXISTS idx_security_users_tenant_enabled
    ON security_users (tenant_id, enabled);

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:USER_CREATE')::UUID, 'USER_CREATE', 'Create merchant users.'),
    (md5('permission:USER_UPDATE')::UUID, 'USER_UPDATE', 'Update merchant users.'),
    (md5('permission:USER_DISABLE')::UUID, 'USER_DISABLE', 'Disable merchant users.'),
    (md5('permission:USER_REACTIVATE')::UUID, 'USER_REACTIVATE', 'Reactivate merchant users.'),
    (md5('permission:USER_ASSIGN_STORE')::UUID, 'USER_ASSIGN_STORE', 'Assign merchant users to stores.'),
    (md5('permission:USER_REMOVE_STORE_ASSIGNMENT')::UUID, 'USER_REMOVE_STORE_ASSIGNMENT', 'Remove merchant user store assignments.'),
    (md5('permission:USER_VIEW_ASSIGNED_STORE_USERS')::UUID, 'USER_VIEW_ASSIGNED_STORE_USERS', 'View users assigned to stores managed by the actor.'),
    (md5('permission:STORE_ACCESS')::UUID, 'STORE_ACCESS', 'Access assigned store operations.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'USER_VIEW',
    'USER_CREATE',
    'USER_UPDATE',
    'USER_DISABLE',
    'USER_REACTIVATE',
    'USER_ASSIGN_STORE',
    'USER_REMOVE_STORE_ASSIGNMENT',
    'STORE_ACCESS',
    'STORE_MANAGE'
)
WHERE role.name IN ('TENANT_OWNER', 'OWNER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'USER_VIEW_ASSIGNED_STORE_USERS',
    'STORE_ACCESS',
    'STORE_MANAGE'
)
WHERE role.name IN ('STORE_MANAGER', 'MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('STORE_ACCESS')
WHERE role.name = 'CASHIER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
