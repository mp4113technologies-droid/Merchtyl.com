ALTER TABLE sale_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE sale_items ALTER COLUMN product_sku DROP NOT NULL;
ALTER TABLE sale_items ADD COLUMN line_type VARCHAR(32) NOT NULL DEFAULT 'CATALOG_PRODUCT';
ALTER TABLE sale_items ADD COLUMN custom_item_tax_treatment VARCHAR(32);
ALTER TABLE sale_items ADD COLUMN tax_category_snapshot_id UUID;
ALTER TABLE return_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE return_items ALTER COLUMN product_sku DROP NOT NULL;

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_line_type
    CHECK (line_type IN ('CATALOG_PRODUCT', 'CUSTOM_ITEM'));
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_custom_tax_treatment
    CHECK (custom_item_tax_treatment IS NULL OR custom_item_tax_treatment IN ('TAXABLE', 'NON_TAXABLE'));
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_shape CHECK (
    (line_type = 'CATALOG_PRODUCT' AND product_id IS NOT NULL AND custom_item_tax_treatment IS NULL)
    OR
    (line_type = 'CUSTOM_ITEM' AND product_id IS NULL AND variant_id IS NULL
        AND product_sku IS NULL AND custom_item_tax_treatment IS NOT NULL)
);

INSERT INTO security_permissions (id, code, description)
SELECT gen_random_uuid(), 'POS_CUSTOM_ITEM', 'Add custom-priced non-catalog items at Retail POS.'
WHERE NOT EXISTS (SELECT 1 FROM security_permissions WHERE code = 'POS_CUSTOM_ITEM');

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id
FROM security_roles r CROSS JOIN security_permissions p
WHERE r.name IN ('OWNER','TENANT_OWNER','MANAGER','STORE_MANAGER','CASHIER')
  AND p.code = 'POS_CUSTOM_ITEM'
ON CONFLICT (role_id, permission_id) DO NOTHING;
