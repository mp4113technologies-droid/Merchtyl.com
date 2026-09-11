ALTER TABLE product_variants ADD COLUMN deposit_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product_variants ADD COLUMN deposit_type VARCHAR(16);
ALTER TABLE product_variants ADD COLUMN deposit_amount NUMERIC(19,4);

ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_deposit_type
    CHECK (deposit_type IS NULL OR deposit_type IN ('BOTTLE','CAN','CASE','OTHER'));
ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_deposit
    CHECK ((deposit_enabled = FALSE AND deposit_type IS NULL AND deposit_amount IS NULL)
        OR (deposit_enabled = TRUE AND deposit_type IS NOT NULL AND deposit_amount > 0));

ALTER TABLE sale_items ADD COLUMN deposit_type VARCHAR(16);
ALTER TABLE sale_items ADD COLUMN deposit_unit_amount NUMERIC(19,4);
ALTER TABLE sale_items ADD COLUMN deposit_quantity NUMERIC(12,4) NOT NULL DEFAULT 0;
ALTER TABLE sale_items ADD COLUMN deposit_total NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_deposit_type
    CHECK (deposit_type IS NULL OR deposit_type IN ('BOTTLE','CAN','CASE','OTHER'));
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_deposit
    CHECK ((deposit_type IS NULL AND deposit_unit_amount IS NULL AND deposit_quantity = 0 AND deposit_total = 0)
        OR (deposit_type IS NOT NULL AND deposit_unit_amount > 0 AND deposit_quantity > 0 AND deposit_total >= 0));

ALTER TABLE return_items ADD COLUMN original_deposit_type VARCHAR(16);
ALTER TABLE return_items ADD COLUMN original_deposit_unit_amount NUMERIC(19,4);
ALTER TABLE return_items ADD COLUMN original_deposit_quantity NUMERIC(12,4) NOT NULL DEFAULT 0;
ALTER TABLE return_items ADD COLUMN original_deposit_total NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE return_items ADD COLUMN return_deposit_total NUMERIC(12,2) NOT NULL DEFAULT 0;
