CREATE TABLE inventory_import_batches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL REFERENCES stores(id),
    filename VARCHAR(255) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES security_users(id),
    status VARCHAR(32) NOT NULL,
    payload_json TEXT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_by UUID REFERENCES security_users(id),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    changed_rows INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_inventory_import_batches_tenant_store ON inventory_import_batches(tenant_id, store_id, created_at DESC);
