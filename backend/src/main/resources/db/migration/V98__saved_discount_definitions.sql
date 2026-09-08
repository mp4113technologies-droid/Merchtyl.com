CREATE TABLE discount_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(40) NOT NULL,
    value NUMERIC(12,4) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL,
    created_by UUID NOT NULL REFERENCES security_users(id),
    updated_by UUID NOT NULL REFERENCES security_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_discount_definitions_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_discount_definitions_value_positive CHECK (value > 0),
    CONSTRAINT ck_discount_definitions_type CHECK (type IN ('DISCOUNT_PERCENTAGE','DISCOUNT_AMOUNT'))
);
CREATE INDEX ix_discount_definitions_tenant_active ON discount_definitions(tenant_id, active, name);

ALTER TABLE sales ADD COLUMN discount_definition_id UUID REFERENCES discount_definitions(id);
ALTER TABLE sales ADD COLUMN discount_name VARCHAR(120);
ALTER TABLE sales ADD COLUMN discount_type VARCHAR(40);
ALTER TABLE sales ADD COLUMN discount_value NUMERIC(12,4);
ALTER TABLE sales ADD COLUMN discount_reason VARCHAR(500);

INSERT INTO security_permissions(id, code, description, created_at, updated_at, version)
VALUES (gen_random_uuid(),'DISCOUNT_VIEW','View saved POS discounts.',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0),
       (gen_random_uuid(),'DISCOUNT_MANAGE','Manage saved POS discounts.',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id, created_at, updated_at, version)
SELECT gen_random_uuid(),r.id,p.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM security_roles r CROSS JOIN security_permissions p
WHERE r.name IN ('OWNER','TENANT_OWNER','MANAGER','STORE_MANAGER') AND p.code IN ('DISCOUNT_VIEW','DISCOUNT_MANAGE')
ON CONFLICT DO NOTHING;
