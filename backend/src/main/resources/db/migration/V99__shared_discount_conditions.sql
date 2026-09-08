ALTER TABLE discount_definitions ADD COLUMN minimum_purchase_amount NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN maximum_purchase_amount NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN maximum_discount_amount NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN minimum_quantity INTEGER;
ALTER TABLE discount_definitions ADD COLUMN all_stores BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE discount_definitions ADD COLUMN starts_at TIMESTAMPTZ;
ALTER TABLE discount_definitions ADD COLUMN ends_at TIMESTAMPTZ;

CREATE TABLE discount_definition_stores (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    PRIMARY KEY (discount_definition_id, store_id)
);
CREATE TABLE discount_definition_categories (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (discount_definition_id, category_id)
);
CREATE TABLE discount_definition_products (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    PRIMARY KEY (discount_definition_id, product_id)
);
CREATE INDEX ix_discount_definition_stores_store ON discount_definition_stores(store_id, discount_definition_id);

INSERT INTO security_role_permissions(id, role_id, permission_id, created_at, updated_at, version)
SELECT gen_random_uuid(),r.id,p.id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM security_roles r CROSS JOIN security_permissions p
WHERE r.name IN ('CASHIER','KITCHEN') AND p.code IN ('DISCOUNT_VIEW','POS_SALE_DISCOUNT')
ON CONFLICT DO NOTHING;
