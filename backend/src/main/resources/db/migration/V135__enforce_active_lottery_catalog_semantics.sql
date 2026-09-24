-- Correct only the active catalog. Completed sale_items are immutable financial snapshots
-- and are intentionally not updated by this migration.
UPDATE products product
SET sellable_type = 'LOTTERY_PRODUCT',
    tax_category_id = NULL,
    decimal_quantity_allowed = FALSE,
    updated_at = now(),
    version = product.version + 1
FROM categories category
WHERE product.category_id = category.id
  AND product.tenant_id = category.tenant_id
  AND product.active = TRUE
  AND product.deleted_at IS NULL
  AND category.system_managed = TRUE
  AND category.system_type = 'LOTTERY'
  AND (product.sellable_type IS DISTINCT FROM 'LOTTERY_PRODUCT'
       OR product.tax_category_id IS NOT NULL
       OR product.decimal_quantity_allowed IS DISTINCT FROM FALSE);

UPDATE products product
SET category_id = category.id,
    tax_category_id = NULL,
    decimal_quantity_allowed = FALSE,
    updated_at = now(),
    version = product.version + 1
FROM categories category
WHERE product.tenant_id = category.tenant_id
  AND product.active = TRUE
  AND product.deleted_at IS NULL
  AND product.sellable_type = 'LOTTERY_PRODUCT'
  AND category.system_managed = TRUE
  AND category.system_type = 'LOTTERY'
  AND (product.category_id IS DISTINCT FROM category.id
       OR product.tax_category_id IS NOT NULL
       OR product.decimal_quantity_allowed IS DISTINCT FROM FALSE);

-- Product tax-category assignments are not used to override the semantic rule. Disable stale
-- active assignments so administration screens do not continue to advertise a taxable setup.
UPDATE product_tax_category_assignments assignment
SET active = FALSE,
    updated_at = now(),
    version = assignment.version + 1
FROM products product
WHERE assignment.product_id = product.id
  AND product.active = TRUE
  AND product.deleted_at IS NULL
  AND product.sellable_type = 'LOTTERY_PRODUCT'
  AND assignment.active = TRUE;
