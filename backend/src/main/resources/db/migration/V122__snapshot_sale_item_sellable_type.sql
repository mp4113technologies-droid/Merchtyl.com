ALTER TABLE sale_items ADD COLUMN sellable_type_snapshot VARCHAR(32);

UPDATE sale_items si
SET sellable_type_snapshot = p.sellable_type
FROM products p
WHERE si.product_id = p.id
  AND si.sellable_type_snapshot IS NULL;

CREATE INDEX idx_sale_items_sellable_type_snapshot
    ON sale_items (sellable_type_snapshot);
