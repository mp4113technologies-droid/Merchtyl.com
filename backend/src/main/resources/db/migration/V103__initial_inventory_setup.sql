ALTER TABLE products ADD COLUMN product_reference VARCHAR(32);

WITH numbered AS (
    SELECT id, 'PRD-' || lpad(row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id)::text, 6, '0') reference
    FROM products
)
UPDATE products p SET product_reference = numbered.reference FROM numbered WHERE numbered.id = p.id;

ALTER TABLE products ALTER COLUMN product_reference SET NOT NULL;
ALTER TABLE products ADD CONSTRAINT chk_products_reference_format CHECK (product_reference ~ '^PRD-[0-9]{6,}$');
CREATE UNIQUE INDEX uq_products_tenant_reference ON products(tenant_id, product_reference) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_products_reference_lower ON products(lower(product_reference));

CREATE TABLE tenant_product_reference_sequences (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    next_value BIGINT NOT NULL CHECK (next_value > 0)
);

INSERT INTO tenant_product_reference_sequences(tenant_id, next_value)
SELECT tenant_id, COALESCE(max(substring(product_reference from 5)::bigint), 0) + 1
FROM products WHERE tenant_id IS NOT NULL GROUP BY tenant_id;

CREATE OR REPLACE FUNCTION next_product_reference(p_tenant_id UUID) RETURNS VARCHAR
LANGUAGE plpgsql AS $$
DECLARE allocated BIGINT;
BEGIN
    INSERT INTO tenant_product_reference_sequences(tenant_id, next_value) VALUES (p_tenant_id, 2)
    ON CONFLICT (tenant_id) DO UPDATE SET next_value = tenant_product_reference_sequences.next_value + 1
    RETURNING next_value - 1 INTO allocated;
    RETURN 'PRD-' || lpad(allocated::text, 6, '0');
END;
$$;

CREATE OR REPLACE FUNCTION assign_product_reference() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.product_reference IS NULL AND NEW.tenant_id IS NOT NULL THEN
        NEW.product_reference := next_product_reference(NEW.tenant_id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_products_assign_reference BEFORE INSERT ON products
FOR EACH ROW EXECUTE FUNCTION assign_product_reference();

CREATE TABLE initial_inventory_imports (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    template_version INTEGER NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES security_users(id) ON DELETE RESTRICT,
    total_rows INTEGER NOT NULL DEFAULT 0,
    product_count INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    warning_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    result_json TEXT,
    failure_code VARCHAR(80),
    expires_at TIMESTAMPTZ NOT NULL,
    validated_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_initial_inventory_import_status CHECK (status IN ('VALIDATED','VALIDATION_FAILED','PROCESSING','COMPLETED','FAILED','EXPIRED'))
);
CREATE INDEX idx_initial_inventory_import_store ON initial_inventory_imports(tenant_id, store_id, created_at DESC);

CREATE TABLE initial_inventory_import_rows (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES initial_inventory_imports(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    product_reference VARCHAR(32),
    product_name VARCHAR(180) NOT NULL,
    variant_name VARCHAR(180) NOT NULL,
    barcode VARCHAR(128),
    sku VARCHAR(64) NOT NULL,
    selling_price NUMERIC(19,4) NOT NULL,
    cost_price NUMERIC(19,4) NOT NULL,
    opening_quantity NUMERIC(19,4) NOT NULL,
    low_stock_level NUMERIC(19,4),
    category VARCHAR(180),
    brand VARCHAR(180),
    unit_code VARCHAR(64),
    tax_category VARCHAR(180) NOT NULL,
    supplier VARCHAR(180),
    age_restricted BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    product_action VARCHAR(32) NOT NULL,
    variant_action VARCHAR(32) NOT NULL,
    product_id UUID,
    variant_id UUID,
    error_codes TEXT NOT NULL DEFAULT '',
    CONSTRAINT uq_initial_inventory_import_row UNIQUE(import_id, row_number)
);
