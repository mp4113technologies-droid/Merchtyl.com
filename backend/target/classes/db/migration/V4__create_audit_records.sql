CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id UUID,
    store_id UUID,
    register_id UUID,
    before_snapshot JSONB,
    after_snapshot JSONB,
    reason VARCHAR(1000),
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_audit_records_action_nonblank CHECK (length(trim(action)) > 0),
    CONSTRAINT ck_audit_records_entity_type_nonblank CHECK (length(trim(entity_type)) > 0)
);

CREATE INDEX idx_audit_records_created_at ON audit_records (created_at DESC);
CREATE INDEX idx_audit_records_action ON audit_records (action);
CREATE INDEX idx_audit_records_entity_type ON audit_records (entity_type);
CREATE INDEX idx_audit_records_entity_id ON audit_records (entity_id);
CREATE INDEX idx_audit_records_actor_user_id ON audit_records (actor_user_id);
CREATE INDEX idx_audit_records_store_id ON audit_records (store_id);
CREATE INDEX idx_audit_records_register_id ON audit_records (register_id);
