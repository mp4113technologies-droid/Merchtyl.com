ALTER TABLE audit_records ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

UPDATE audit_records a SET tenant_id = u.tenant_id
FROM security_users u WHERE a.actor_user_id = u.id AND a.tenant_id IS NULL;

UPDATE audit_records a SET tenant_id = s.tenant_id
FROM stores s WHERE a.store_id = s.id AND a.tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_audit_records_tenant_created
    ON audit_records (tenant_id, created_at DESC);
