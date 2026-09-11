ALTER TABLE discount_definitions DROP CONSTRAINT ck_discount_definitions_type;
ALTER TABLE discount_definitions ADD CONSTRAINT ck_discount_definitions_type
    CHECK (type IN ('DISCOUNT_PERCENTAGE','DISCOUNT_AMOUNT','MULTI_BUY_FIXED_PRICE'));

ALTER TABLE discount_definitions ADD COLUMN promotion_domain VARCHAR(24);
ALTER TABLE discount_definitions ADD COLUMN buy_quantity INTEGER;
ALTER TABLE discount_definitions ADD COLUMN bundle_price NUMERIC(12,4);
ALTER TABLE discount_definitions ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;
ALTER TABLE discount_definitions ADD COLUMN stackable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE discount_definitions ADD CONSTRAINT ck_discount_definitions_multi_buy
    CHECK (type <> 'MULTI_BUY_FIXED_PRICE' OR
           (promotion_domain IN ('RETAIL','FOOD_SERVICE') AND buy_quantity >= 2 AND bundle_price > 0));

CREATE TABLE discount_definition_targets (
    discount_definition_id UUID NOT NULL REFERENCES discount_definitions(id) ON DELETE CASCADE,
    target_type VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    PRIMARY KEY (discount_definition_id, target_type, target_id),
    CONSTRAINT ck_discount_definition_target_type CHECK (target_type IN
        ('PRODUCT','PRODUCT_VARIANT','PRODUCT_CATEGORY','MENU_ITEM','MENU_ITEM_VARIANT','MENU_CATEGORY'))
);
CREATE INDEX ix_discount_definition_targets_lookup
    ON discount_definition_targets(target_type, target_id, discount_definition_id);

ALTER TABLE sale_items ADD COLUMN promotion_id UUID;
ALTER TABLE sale_items ADD COLUMN promotion_name VARCHAR(120);
ALTER TABLE sale_items ADD COLUMN promotion_type VARCHAR(40);
ALTER TABLE sale_items ADD COLUMN promotion_buy_quantity INTEGER;
ALTER TABLE sale_items ADD COLUMN promotion_bundle_price NUMERIC(12,4);
ALTER TABLE sale_items ADD COLUMN promotion_regular_amount NUMERIC(12,2);
ALTER TABLE sale_items ADD COLUMN promotion_discount_amount NUMERIC(12,2);
ALTER TABLE sale_items ADD COLUMN promotion_final_amount NUMERIC(12,2);
ALTER TABLE sale_items ADD COLUMN menu_item_id UUID;
ALTER TABLE sale_items ADD COLUMN menu_item_variant_id UUID;
ALTER TABLE sale_items ADD COLUMN item_name_snapshot VARCHAR(180);
ALTER TABLE sale_items ADD COLUMN menu_variant_name_snapshot VARCHAR(180);
