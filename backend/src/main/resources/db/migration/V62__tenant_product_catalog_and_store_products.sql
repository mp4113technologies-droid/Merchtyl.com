ALTER TABLE products ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT;
ALTER TABLE product_barcodes ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT;
ALTER TABLE product_variants ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT;

CREATE TABLE product_catalog_migration_issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    issue_code VARCHAR(64) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_catalog_migration_issue UNIQUE (product_id, issue_code)
);

WITH product_tenants AS (
    SELECT product_id, min(tenant_id::text)::uuid tenant_id, count(DISTINCT tenant_id) tenant_count
    FROM (
        SELECT ib.product_id, s.tenant_id FROM inventory_balances ib JOIN stores s ON s.id=ib.store_id
        UNION ALL
        SELECT si.product_id, st.tenant_id FROM sale_items si JOIN sales sa ON sa.id=si.sale_id JOIN stores st ON st.id=sa.store_id
    ) candidates WHERE tenant_id IS NOT NULL GROUP BY product_id
)
UPDATE products p SET tenant_id=pt.tenant_id FROM product_tenants pt
WHERE p.id=pt.product_id AND pt.tenant_count=1;

INSERT INTO product_catalog_migration_issues(product_id, issue_code, details)
SELECT p.id, 'TENANT_UNRESOLVED', 'No deterministic single tenant could be inferred from store inventory or sales history.'
FROM products p WHERE p.tenant_id IS NULL
ON CONFLICT DO NOTHING;

UPDATE product_barcodes b SET tenant_id=p.tenant_id FROM products p WHERE p.id=b.product_id;
UPDATE product_variants v SET tenant_id=p.tenant_id FROM products p WHERE p.id=v.product_id;

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_sku_key;
ALTER TABLE product_variants DROP CONSTRAINT IF EXISTS product_variants_sku_key;
ALTER TABLE product_barcodes DROP CONSTRAINT IF EXISTS product_barcodes_barcode_key;
DROP INDEX IF EXISTS uq_products_sku_lower;
DROP INDEX IF EXISTS uq_product_variants_sku_lower;
DROP INDEX IF EXISTS uq_product_barcodes_barcode_lower;
CREATE UNIQUE INDEX uq_products_tenant_sku_lower ON products(tenant_id, lower(sku)) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_product_variants_tenant_sku_lower ON product_variants(tenant_id, lower(sku)) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_product_barcodes_tenant_barcode_lower ON product_barcodes(tenant_id, lower(barcode)) WHERE tenant_id IS NOT NULL;

CREATE TABLE store_products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sellable BOOLEAN NOT NULL DEFAULT TRUE,
    selling_price NUMERIC(19,4) NOT NULL,
    cost_price NUMERIC(19,4),
    minimum_selling_price NUMERIC(19,4),
    low_stock_threshold NUMERIC(19,4),
    allow_discount BOOLEAN NOT NULL DEFAULT TRUE,
    allow_price_override BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_store_products_tenant_store_product UNIQUE(tenant_id, store_id, product_id),
    CONSTRAINT ck_store_products_prices CHECK (selling_price >= 0 AND (cost_price IS NULL OR cost_price >= 0) AND (minimum_selling_price IS NULL OR minimum_selling_price >= 0))
);
CREATE INDEX idx_store_products_store_sellable ON store_products(tenant_id, store_id, active, sellable);
CREATE INDEX idx_store_products_product ON store_products(tenant_id, product_id);

INSERT INTO store_products(id, tenant_id, store_id, product_id, selling_price, cost_price)
SELECT gen_random_uuid(), p.tenant_id, candidates.store_id, p.id, p.price, p.cost
FROM products p JOIN (
    SELECT DISTINCT ib.product_id, ib.store_id FROM inventory_balances ib
    UNION
    SELECT DISTINCT si.product_id, sa.store_id FROM sale_items si JOIN sales sa ON sa.id=si.sale_id
) candidates ON candidates.product_id=p.id
JOIN stores s ON s.id=candidates.store_id AND s.tenant_id=p.tenant_id
WHERE p.tenant_id IS NOT NULL
ON CONFLICT (tenant_id, store_id, product_id) DO NOTHING;
