ALTER TABLE security_users
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS created_by_role VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE security_users
    ADD CONSTRAINT fk_security_users_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES security_users (id) ON DELETE SET NULL;

ALTER TABLE security_users
    ADD CONSTRAINT fk_security_users_updated_by_user
        FOREIGN KEY (updated_by_user_id) REFERENCES security_users (id) ON DELETE SET NULL;

ALTER TABLE security_users
    ADD CONSTRAINT ck_security_users_created_by_role
        CHECK (created_by_role IS NULL OR created_by_role IN ('TENANT_OWNER', 'STORE_MANAGER'));

CREATE INDEX IF NOT EXISTS idx_security_users_tenant_role
    ON security_users (tenant_id, enabled, locked);

CREATE INDEX IF NOT EXISTS idx_security_users_tenant_created_by
    ON security_users (tenant_id, created_by_user_id);

CREATE INDEX IF NOT EXISTS idx_security_user_store_assignments_overlap
    ON security_user_store_assignments (tenant_id, store_id, active, assignment_role, user_id);

INSERT INTO security_permissions (id, code, description)
VALUES (md5('permission:MANAGER_CREATE_MANAGER')::UUID, 'MANAGER_CREATE_MANAGER', 'Allow a store manager to create or promote scoped store managers.')
ON CONFLICT (code) DO NOTHING;
