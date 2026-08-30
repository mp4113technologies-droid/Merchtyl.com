ALTER TABLE stores ADD COLUMN kitchen_display_name VARCHAR(180);

CREATE TABLE store_capabilities (
    store_id UUID NOT NULL,
    capability VARCHAR(40) NOT NULL,
    PRIMARY KEY (store_id, capability),
    CONSTRAINT fk_store_capabilities_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    CONSTRAINT ck_store_capabilities_value CHECK (capability IN ('RETAIL', 'FOOD_SERVICE'))
);

INSERT INTO store_capabilities (store_id, capability)
SELECT id, 'RETAIL' FROM stores;

CREATE INDEX idx_store_capabilities_capability ON store_capabilities (capability, store_id);

ALTER TABLE stores ADD CONSTRAINT ck_stores_kitchen_display_name_nonblank
    CHECK (kitchen_display_name IS NULL OR length(trim(kitchen_display_name)) > 0);

CREATE TABLE tenant_store_operation_defaults (
    tenant_id UUID NOT NULL,
    capability VARCHAR(40) NOT NULL,
    kitchen_display_name VARCHAR(180),
    PRIMARY KEY (tenant_id, capability),
    CONSTRAINT fk_tenant_store_operation_defaults_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_store_operation_defaults_value CHECK (capability IN ('RETAIL', 'FOOD_SERVICE'))
);

INSERT INTO tenant_store_operation_defaults (tenant_id, capability)
SELECT id, 'RETAIL' FROM tenants;
