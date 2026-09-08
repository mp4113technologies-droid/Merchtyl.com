-- V99 was exercised against development databases while the shared discount
-- feature was still being completed. Keep the released schema forward-safe by
-- reconciling every shared-discount object without replacing historical data.

ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS minimum_purchase_amount NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS maximum_purchase_amount NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS maximum_discount_amount NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS minimum_quantity INTEGER;
ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS all_stores BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS starts_at TIMESTAMPTZ;
ALTER TABLE discount_definitions ADD COLUMN IF NOT EXISTS ends_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS discount_definition_stores (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    PRIMARY KEY (discount_definition_id, store_id)
);

CREATE TABLE IF NOT EXISTS discount_definition_categories (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (discount_definition_id, category_id)
);

CREATE TABLE IF NOT EXISTS discount_definition_products (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    PRIMARY KEY (discount_definition_id, product_id)
);

CREATE INDEX IF NOT EXISTS ix_discount_definition_stores_store
    ON discount_definition_stores(store_id, discount_definition_id);

INSERT INTO security_role_permissions(id, role_id, permission_id, created_at, updated_at, version)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM security_roles r
CROSS JOIN security_permissions p
WHERE r.name IN ('CASHIER', 'KITCHEN')
  AND p.code IN ('DISCOUNT_VIEW', 'POS_SALE_DISCOUNT')
ON CONFLICT DO NOTHING;
