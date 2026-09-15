ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_line_type;
ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_shape;

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_line_type
    CHECK (line_type IN ('CATALOG_PRODUCT', 'CUSTOM_ITEM', 'LOTTERY_SOLD', 'LOTTERY_WIN'));

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_shape CHECK (
    (line_type = 'CATALOG_PRODUCT' AND product_id IS NOT NULL AND custom_item_tax_treatment IS NULL)
    OR
    (line_type = 'CUSTOM_ITEM' AND product_id IS NULL AND variant_id IS NULL
        AND product_sku IS NULL AND custom_item_tax_treatment IS NOT NULL)
    OR
    (line_type IN ('LOTTERY_SOLD', 'LOTTERY_WIN') AND product_id IS NULL AND variant_id IS NULL
        AND product_sku IS NULL AND custom_item_tax_treatment IS NULL)
);
