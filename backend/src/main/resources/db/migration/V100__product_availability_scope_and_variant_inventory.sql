ALTER TABLE products ADD COLUMN availability_scope VARCHAR(32) NOT NULL DEFAULT 'SELECTED_STORES';
ALTER TABLE products ADD CONSTRAINT ck_products_availability_scope CHECK (availability_scope IN ('ALL_STORES','SELECTED_STORES'));

ALTER TABLE inventory_balances ADD COLUMN variant_id UUID REFERENCES product_variants(id) ON DELETE RESTRICT;
ALTER TABLE inventory_transactions ADD COLUMN variant_id UUID REFERENCES product_variants(id) ON DELETE RESTRICT;
ALTER TABLE stock_adjustment_lines ADD COLUMN variant_id UUID REFERENCES product_variants(id) ON DELETE RESTRICT;
ALTER TABLE stock_count_lines ADD COLUMN variant_id UUID REFERENCES product_variants(id) ON DELETE RESTRICT;
ALTER TABLE stock_count_lines DROP CONSTRAINT uq_stock_count_lines_count_product;
ALTER TABLE stock_count_lines ADD CONSTRAINT uq_stock_count_lines_count_product_variant
    UNIQUE NULLS NOT DISTINCT (stock_count_id, product_id, variant_id);

ALTER TABLE inventory_balances DROP CONSTRAINT uq_inventory_balances_store_product;
ALTER TABLE inventory_balances ADD CONSTRAINT uq_inventory_balances_store_product_variant
    UNIQUE NULLS NOT DISTINCT (store_id, product_id, variant_id);
CREATE INDEX idx_inventory_balances_store_variant ON inventory_balances(store_id, variant_id);
CREATE INDEX idx_inventory_transactions_store_variant ON inventory_transactions(store_id, variant_id);

-- Existing quantities remain attached to their original Store and base Product.
-- They are deliberately not distributed among variants because that cannot be inferred safely.
